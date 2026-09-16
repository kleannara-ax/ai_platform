package com.company.module.steamenergy.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 스팀에너지관리 프론트엔드 페이지 라우팅
 *
 * <p>/steam-energy/page -&gt; steam-energy/index (Thymeleaf 렌더링)
 * <p>플랫폼 메뉴(STEAM_ENERGY_MGMT)의 진입 URL.
 */
@Controller
public class SteamEnergyPageController {

    @GetMapping({"/steam-energy/page", "/steam-energy/page/"})
    public String index() {
        return "steam-energy/index";
    }
}
