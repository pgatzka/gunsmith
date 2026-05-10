package io.github.pgatzka.gunsmith.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Param;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.impl.DSL;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static io.github.pgatzka.gunsmith.jooq.Tables.ATTACHMENT;
import static io.github.pgatzka.gunsmith.jooq.Tables.BUILD;
import static io.github.pgatzka.gunsmith.jooq.Tables.CATEGORY;
import static io.github.pgatzka.gunsmith.jooq.Tables.WEAPON;

/**
 * Read-only data access for the Thymeleaf web layer. All queries here are bounded by either a
 * weapon scope or a single build id, and the build queries use keyset pagination on
 * (created_at desc, id desc) so they remain stable as the BUILD table grows past millions of rows.
 *
 * <p>Performance note: the JSONB filter / pinned queries do {@code jsonb_path_exists} scans across
 * all builds for a weapon. At very large scale a GIN index on {@code build.json jsonb_path_ops} or
 * a derived {@code build_attachment} side table will become necessary; both are out of scope here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BuildPageService {

    private final DSLContext dsl;

    public record WeaponRow(int id, String name, String iconLink, @Nullable String category) {}

    public record WeaponHeader(int id, String name, String iconLink, @Nullable String category,
                               double ergonomics, int recoilHorizontal, int recoilVertical) {}

    public record AttachmentOption(int id, String name, String iconLink) {}

    public record BuildSummary(int id, OffsetDateTime createdAt,
                               double ergonomics, double recoilHorizontal, double recoilVertical) {}

    public record PinnedBuild(String label, BuildSummary summary) {}

    public record TreeRow(int depth, String slotName,
                          @Nullable Integer attachmentId,
                          @Nullable String attachmentName,
                          @Nullable String attachmentIcon) {}

    public record BuildDetail(int id, OffsetDateTime createdAt,
                              double ergonomics, double recoilHorizontal, double recoilVertical,
                              WeaponHeader weapon, List<TreeRow> tree) {}

    // -- weapon list ----------------------------------------------------------

    public List<WeaponRow> listWeapons(int limit, int offset) {
        return dsl.select(WEAPON.ID, WEAPON.NAME, WEAPON.ICON_LINK, CATEGORY.NAME.as("category"))
                .from(WEAPON)
                .leftJoin(CATEGORY).on(CATEGORY.ID.eq(WEAPON.CATEGORY_ID))
                .orderBy(WEAPON.NAME.asc(), WEAPON.ID.asc())
                .limit(limit)
                .offset(offset)
                .fetch(r -> new WeaponRow(
                        r.get(WEAPON.ID),
                        r.get(WEAPON.NAME),
                        r.get(WEAPON.ICON_LINK),
                        r.get("category", String.class)));
    }

    public @Nullable WeaponHeader getWeaponHeader(int weaponId) {
        return dsl.select(WEAPON.ID, WEAPON.NAME, WEAPON.ICON_LINK, CATEGORY.NAME.as("category"),
                          WEAPON.ERGONOMICS, WEAPON.RECOIL_HORIZONTAL, WEAPON.RECOIL_VERTICAL)
                .from(WEAPON)
                .leftJoin(CATEGORY).on(CATEGORY.ID.eq(WEAPON.CATEGORY_ID))
                .where(WEAPON.ID.eq(weaponId))
                .fetchOne(r -> new WeaponHeader(
                        r.get(WEAPON.ID),
                        r.get(WEAPON.NAME),
                        r.get(WEAPON.ICON_LINK),
                        r.get("category", String.class),
                        r.get(WEAPON.ERGONOMICS),
                        r.get(WEAPON.RECOIL_HORIZONTAL),
                        r.get(WEAPON.RECOIL_VERTICAL)));
    }

    // -- attachment options sidebar ------------------------------------------

    /**
     * Distinct attachments that actually appear in at least one build for the given weapon.
     * Walks the build JSON via {@code jsonb_path_query} and joins to the attachment table for
     * names + icons.
     */
    public List<AttachmentOption> listAttachmentOptionsForWeapon(int weaponId) {
        String sql = """
                select a.id, a.name, a.icon_link
                from attachment a
                where a.id in (
                    select distinct ((j.value)#>>'{}')::int
                    from build b
                    cross join lateral jsonb_path_query(b.json, '$.**.attachmentId') as j(value)
                    where b.weapon_id = ?
                      and jsonb_typeof(j.value) = 'number'
                )
                order by a.name
                """;
        return dsl.fetch(sql, weaponId).map(r -> new AttachmentOption(
                r.get("id", Integer.class),
                r.get("name", String.class),
                r.get("icon_link", String.class)));
    }

    // -- build list (keyset + JSONB filter) ----------------------------------

    public List<BuildSummary> listBuilds(int weaponId,
                                         Set<Integer> include,
                                         Set<Integer> exclude,
                                         Set<Integer> excludeIds,
                                         @Nullable OffsetDateTime cursorTs,
                                         @Nullable Integer cursorId,
                                         int limit) {

        Condition where = BUILD.WEAPON_ID.eq(weaponId);
        where = where.and(jsonbAttachmentFilter(include, exclude));

        if (!excludeIds.isEmpty()) {
            where = where.and(BUILD.ID.notIn(excludeIds));
        }

        if (cursorTs != null && cursorId != null) {
            where = where.and(
                    BUILD.CREATED_AT.lt(cursorTs)
                            .or(BUILD.CREATED_AT.eq(cursorTs).and(BUILD.ID.lt(cursorId))));
        }

        return dsl.select(BUILD.ID, BUILD.CREATED_AT, BUILD.ERGONOMICS,
                          BUILD.RECOIL_HORIZONTAL, BUILD.RECOIL_VERTICAL)
                .from(BUILD)
                .where(where)
                .orderBy(BUILD.CREATED_AT.desc(), BUILD.ID.desc())
                .limit(limit)
                .fetch(r -> new BuildSummary(
                        r.get(BUILD.ID),
                        r.get(BUILD.CREATED_AT),
                        r.get(BUILD.ERGONOMICS),
                        r.get(BUILD.RECOIL_HORIZONTAL),
                        r.get(BUILD.RECOIL_VERTICAL)));
    }

    public List<PinnedBuild> findPinnedBuilds(int weaponId, Set<Integer> include, Set<Integer> exclude) {
        Condition base = BUILD.WEAPON_ID.eq(weaponId).and(jsonbAttachmentFilter(include, exclude));

        BuildSummary bestErg = dsl.select(BUILD.ID, BUILD.CREATED_AT, BUILD.ERGONOMICS,
                                          BUILD.RECOIL_HORIZONTAL, BUILD.RECOIL_VERTICAL)
                .from(BUILD)
                .where(base)
                .orderBy(BUILD.ERGONOMICS.desc(), BUILD.ID.desc())
                .limit(1)
                .fetchOne(r -> new BuildSummary(
                        r.get(BUILD.ID), r.get(BUILD.CREATED_AT),
                        r.get(BUILD.ERGONOMICS),
                        r.get(BUILD.RECOIL_HORIZONTAL),
                        r.get(BUILD.RECOIL_VERTICAL)));

        BuildSummary bestVrt = dsl.select(BUILD.ID, BUILD.CREATED_AT, BUILD.ERGONOMICS,
                                          BUILD.RECOIL_HORIZONTAL, BUILD.RECOIL_VERTICAL)
                .from(BUILD)
                .where(base)
                .orderBy(BUILD.RECOIL_VERTICAL.asc(), BUILD.ID.asc())
                .limit(1)
                .fetchOne(r -> new BuildSummary(
                        r.get(BUILD.ID), r.get(BUILD.CREATED_AT),
                        r.get(BUILD.ERGONOMICS),
                        r.get(BUILD.RECOIL_HORIZONTAL),
                        r.get(BUILD.RECOIL_VERTICAL)));

        List<PinnedBuild> out = new ArrayList<>(2);
        if (bestErg != null && bestVrt != null && bestErg.id() == bestVrt.id()) {
            out.add(new PinnedBuild("Best Ergonomics · Best Vertical Recoil", bestErg));
            return out;
        }
        if (bestErg != null) out.add(new PinnedBuild("Best Ergonomics", bestErg));
        if (bestVrt != null) out.add(new PinnedBuild("Best Vertical Recoil", bestVrt));
        return out;
    }

    /**
     * Builds a {@code jsonb_path_exists} predicate for each include / exclude attachment id.
     * The path matches any {@code attachmentId} field anywhere in the build tree.
     */
    private Condition jsonbAttachmentFilter(Set<Integer> include, Set<Integer> exclude) {
        Condition c = DSL.noCondition();
        for (Integer id : include) {
            c = c.and(jsonbHasAttachment(id));
        }
        for (Integer id : exclude) {
            c = c.and(DSL.not(jsonbHasAttachment(id)));
        }
        return c;
    }

    private Condition jsonbHasAttachment(int attachmentId) {
        // Bind the id as a SQL parameter inside the jsonb path variables map, not via string concat.
        Param<Integer> idParam = DSL.val(attachmentId);
        return DSL.condition(
                "jsonb_path_exists({0}, '$.** ? (@.attachmentId == $aid)', jsonb_build_object('aid', {1}))",
                BUILD.JSON, idParam);
    }

    // -- build detail (recursive CTE walking the JSON tree) ------------------

    public @Nullable BuildDetail getBuildDetail(int buildId) {
        Record header = dsl.select(BUILD.ID, BUILD.CREATED_AT, BUILD.WEAPON_ID,
                                   BUILD.ERGONOMICS, BUILD.RECOIL_HORIZONTAL, BUILD.RECOIL_VERTICAL)
                .from(BUILD)
                .where(BUILD.ID.eq(buildId))
                .fetchOne();
        if (header == null) return null;

        WeaponHeader weapon = getWeaponHeader(header.get(BUILD.WEAPON_ID));
        if (weapon == null) return null;

        List<TreeRow> tree = fetchBuildTree(buildId);

        return new BuildDetail(
                header.get(BUILD.ID),
                header.get(BUILD.CREATED_AT),
                header.get(BUILD.ERGONOMICS),
                header.get(BUILD.RECOIL_HORIZONTAL),
                header.get(BUILD.RECOIL_VERTICAL),
                weapon,
                tree);
    }

    /**
     * Recursive CTE that walks {@code build.json} (a tree of slot → attachment → slots → …),
     * resolving each slot id to its name and each attachment id to its name + icon.
     * Rows are returned in pre-order (depth-first, left-to-right) via the path array.
     */
    private List<TreeRow> fetchBuildTree(int buildId) {
        String sql = """
                with recursive tree as (
                    select
                        1 as depth,
                        array[s.ord]::bigint[] as path,
                        (s.value->>'slotId')::int as slot_id,
                        case when s.value->'attachment' is null then null
                             else (s.value->'attachment'->>'attachmentId')::int end as attachment_id,
                        s.value->'attachment' as attachment_node
                    from build b
                    cross join lateral jsonb_array_elements(b.json -> 'slots') with ordinality s(value, ord)
                    where b.id = ?
                    union all
                    select
                        t.depth + 1,
                        t.path || s.ord,
                        (s.value->>'slotId')::int,
                        case when s.value->'attachment' is null then null
                             else (s.value->'attachment'->>'attachmentId')::int end,
                        s.value->'attachment'
                    from tree t
                    cross join lateral jsonb_array_elements(t.attachment_node -> 'slots') with ordinality s(value, ord)
                    where t.attachment_node is not null
                      and jsonb_typeof(t.attachment_node -> 'slots') = 'array'
                )
                select t.depth, t.path,
                       sl.name as slot_name,
                       a.id    as attachment_id,
                       a.name  as attachment_name,
                       a.icon_link as attachment_icon
                from tree t
                left join slot sl on sl.id = t.slot_id
                left join attachment a on a.id = t.attachment_id
                order by t.path
                """;

        Result<Record> rows = dsl.fetch(sql, buildId);
        List<TreeRow> out = new ArrayList<>(rows.size());
        for (Record r : rows) {
            out.add(new TreeRow(
                    r.get("depth", Integer.class),
                    r.get("slot_name", String.class),
                    r.get("attachment_id", Integer.class),
                    r.get("attachment_name", String.class),
                    r.get("attachment_icon", String.class)));
        }
        return out;
    }
}
