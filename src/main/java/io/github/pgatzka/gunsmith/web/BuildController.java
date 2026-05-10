package io.github.pgatzka.gunsmith.web;

import io.github.pgatzka.gunsmith.web.BuildPageService.BuildDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
@RequiredArgsConstructor
public class BuildController {

    private final BuildPageService service;

    @GetMapping("/builds/{buildId}")
    public String detail(@PathVariable int buildId, Model model) {
        BuildDetail detail = service.getBuildDetail(buildId);
        if (detail == null) {
            return "redirect:/weapons";
        }
        model.addAttribute("detail", detail);
        return "builds/detail";
    }
}
