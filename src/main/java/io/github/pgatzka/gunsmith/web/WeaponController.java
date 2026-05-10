package io.github.pgatzka.gunsmith.web;

import io.github.pgatzka.gunsmith.web.BuildPageService.AttachmentOption;
import io.github.pgatzka.gunsmith.web.BuildPageService.BuildSummary;
import io.github.pgatzka.gunsmith.web.BuildPageService.PinnedBuild;
import io.github.pgatzka.gunsmith.web.BuildPageService.WeaponHeader;
import io.github.pgatzka.gunsmith.web.BuildPageService.WeaponRow;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Controller
@RequiredArgsConstructor
public class WeaponController {

    private static final int WEAPON_PAGE_SIZE = 24;
    private static final int BUILD_PAGE_SIZE = 25;

    private final BuildPageService service;

    @GetMapping("/")
    public RedirectView index() {
        return new RedirectView("/weapons");
    }

    @GetMapping("/weapons")
    public String weapons(@RequestParam(name = "page", defaultValue = "0") int page, Model model) {
        int safePage = Math.max(0, page);
        int offset = safePage * WEAPON_PAGE_SIZE;
        List<WeaponRow> rows = service.listWeapons(WEAPON_PAGE_SIZE + 1, offset);
        boolean hasNext = rows.size() > WEAPON_PAGE_SIZE;
        if (hasNext) {
            rows = rows.subList(0, WEAPON_PAGE_SIZE);
        }
        model.addAttribute("weapons", rows);
        model.addAttribute("page", safePage);
        model.addAttribute("hasNext", hasNext);
        model.addAttribute("hasPrev", safePage > 0);
        return "weapons/list";
    }

    @GetMapping("/weapons/{weaponId}/builds")
    public String builds(@PathVariable int weaponId,
                         @RequestParam(name = "include", required = false) @Nullable List<Integer> include,
                         @RequestParam(name = "exclude", required = false) @Nullable List<Integer> exclude,
                         @RequestParam(name = "pin", required = false) @Nullable List<Integer> pinCarry,
                         @RequestParam(name = "after_ts", required = false) @Nullable String afterTs,
                         @RequestParam(name = "after_id", required = false) @Nullable Integer afterId,
                         @RequestParam(name = "q", required = false) @Nullable String query,
                         Model model) {

        WeaponHeader weapon = service.getWeaponHeader(weaponId);
        if (weapon == null) {
            return "redirect:/weapons";
        }

        Set<Integer> includeSet = nonNullSet(include);
        Set<Integer> excludeSet = nonNullSet(exclude);

        List<AttachmentOption> attachmentOptions = service.listAttachmentOptionsForWeapon(weaponId);

        boolean firstPage = afterTs == null || afterId == null;

        // Compute pinned only on the first page; on later pages we still need to know the IDs so we
        // exclude them from the regular keyset list. They round-trip via the `pin` query param.
        List<PinnedBuild> pinnedBuilds = List.of();
        Set<Integer> pinnedIds;
        if (firstPage) {
            pinnedBuilds = service.findPinnedBuilds(weaponId, includeSet, excludeSet);
            pinnedIds = new LinkedHashSet<>();
            for (PinnedBuild p : pinnedBuilds) pinnedIds.add(p.summary().id());
        } else {
            pinnedIds = new LinkedHashSet<>(nonNullSet(pinCarry));
        }

        OffsetDateTime cursorTs = null;
        Integer cursorId = null;
        if (!firstPage) {
            try {
                cursorTs = OffsetDateTime.parse(afterTs);
                cursorId = afterId;
            } catch (Exception ignored) {
                // bad cursor — treat as first page
            }
        }

        List<BuildSummary> rows = service.listBuilds(
                weaponId, includeSet, excludeSet, pinnedIds,
                cursorTs, cursorId, BUILD_PAGE_SIZE + 1);

        boolean hasNext = rows.size() > BUILD_PAGE_SIZE;
        if (hasNext) rows = rows.subList(0, BUILD_PAGE_SIZE);

        BuildSummary tail = rows.isEmpty() ? null : rows.get(rows.size() - 1);

        model.addAttribute("weapon", weapon);
        model.addAttribute("attachmentOptions", attachmentOptions);
        model.addAttribute("includeSet", includeSet);
        model.addAttribute("excludeSet", excludeSet);
        model.addAttribute("pinnedBuilds", pinnedBuilds);
        model.addAttribute("pinnedIds", pinnedIds);
        model.addAttribute("builds", rows);
        model.addAttribute("hasNext", hasNext);
        model.addAttribute("nextCursorTs", tail != null ? tail.createdAt().toString() : null);
        model.addAttribute("nextCursorId", tail != null ? tail.id() : null);
        model.addAttribute("firstPage", firstPage);
        model.addAttribute("query", query == null ? "" : query);
        return "weapons/builds";
    }

    private static Set<Integer> nonNullSet(@Nullable List<Integer> list) {
        if (list == null || list.isEmpty()) return Set.of();
        return new LinkedHashSet<>(list);
    }
}
