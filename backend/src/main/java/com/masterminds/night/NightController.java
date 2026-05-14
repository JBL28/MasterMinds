package com.masterminds.night;

import com.masterminds.night.dto.NightActionResponse;
import com.masterminds.night.dto.NightTargetRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rooms/{code}/night")
public class NightController {

    private final NightService nightService;

    public NightController(NightService nightService) {
        this.nightService = nightService;
    }

    @PostMapping("/kill")
    public NightActionResponse kill(
            @PathVariable String code,
            @RequestBody NightTargetRequest request
    ) {
        return toResponse(nightService.nightKill(code, request.playerToken(), request.targetToken()));
    }

    @PostMapping("/investigate")
    public NightActionResponse investigate(
            @PathVariable String code,
            @RequestBody NightTargetRequest request
    ) {
        return toResponse(nightService.nightInvestigate(code, request.playerToken(), request.targetToken()));
    }

    @PostMapping("/protect")
    public NightActionResponse protect(
            @PathVariable String code,
            @RequestBody NightTargetRequest request
    ) {
        return toResponse(nightService.nightProtect(code, request.playerToken(), request.targetToken()));
    }

    private NightActionResponse toResponse(NightActionResult result) {
        return new NightActionResponse(
                result.resolved(),
                result.targetToken(),
                result.killedToken(),
                result.protectedTarget(),
                result.mafia()
        );
    }
}
