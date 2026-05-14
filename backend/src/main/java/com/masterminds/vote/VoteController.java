package com.masterminds.vote;

import com.masterminds.room.RoomMapper;
import com.masterminds.room.dto.RoomResponse;
import com.masterminds.vote.dto.GuiltyVoteRequest;
import com.masterminds.vote.dto.LastWordsRequest;
import com.masterminds.vote.dto.LastWordsResponse;
import com.masterminds.vote.dto.NominateVoteRequest;
import com.masterminds.vote.dto.VoteResultResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rooms/{code}/vote")
public class VoteController {

    private final VoteService voteService;

    public VoteController(VoteService voteService) {
        this.voteService = voteService;
    }

    @PostMapping("/nominate")
    public VoteResultResponse nominate(
            @PathVariable String code,
            @RequestBody NominateVoteRequest request
    ) {
        VotePhaseResult result = voteService.nominate(code, request.playerToken(), request.targetToken());
        return new VoteResultResponse(result.resolved(), result.targetToken(), result.executed());
    }

    @PostMapping("/last-words")
    public LastWordsResponse getLastWords(
            @PathVariable String code,
            @RequestBody LastWordsRequest request
    ) {
        return new LastWordsResponse(voteService.getLastWords(code, request.playerToken()));
    }

    @PostMapping("/last-words/complete")
    public RoomResponse completeLastWords(
            @PathVariable String code,
            @RequestBody LastWordsRequest request
    ) {
        return RoomMapper.toRoomResponse(voteService.beginGuiltyVote(code, request.playerToken()));
    }

    @PostMapping("/guilty")
    public VoteResultResponse voteGuilty(
            @PathVariable String code,
            @RequestBody GuiltyVoteRequest request
    ) {
        VotePhaseResult result = voteService.voteGuilty(code, request.playerToken(), request.guilty());
        return new VoteResultResponse(result.resolved(), result.targetToken(), result.executed());
    }
}
