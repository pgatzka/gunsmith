package io.github.pgatzka.gunsmith.web;

import io.github.pgatzka.gunsmith.web.StatsService.ThroughputBucket;
import io.github.pgatzka.gunsmith.web.StatsService.ThroughputReport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Controller
@RequiredArgsConstructor
public class StatsController {

    private static final DateTimeFormatter LABEL_FMT =
            DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    private final StatsService service;

    @GetMapping("/stats/throughput")
    public String throughput(Model model) {
        ThroughputReport report = service.last4Hours();

        List<String> labels = new ArrayList<>(report.buckets().size());
        List<Long> counts = new ArrayList<>(report.buckets().size());
        ZoneId zone = ZoneId.systemDefault();
        for (ThroughputBucket b : report.buckets()) {
            labels.add(b.bucket().atZoneSameInstant(zone).format(LABEL_FMT));
            counts.add(b.count());
        }

        model.addAttribute("report", report);
        model.addAttribute("labels", labels);
        model.addAttribute("counts", counts);
        return "stats/throughput";
    }
}
