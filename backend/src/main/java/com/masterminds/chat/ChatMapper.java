package com.masterminds.chat;

import com.masterminds.chat.dto.DayMessageResponse;
import com.masterminds.chat.dto.DayMessageSubmissionResponse;

public final class ChatMapper {

    private ChatMapper() {
    }

    public static DayMessageSubmissionResponse toSubmissionResponse(DayMessageResult result) {
        return new DayMessageSubmissionResponse(
                result.revealed(),
                result.messages().stream()
                        .map(ChatMapper::toMessageResponse)
                        .toList()
        );
    }

    private static DayMessageResponse toMessageResponse(DayMessage message) {
        return new DayMessageResponse(
                message.playerToken(),
                message.playerName(),
                message.dayTurn(),
                message.message(),
                message.submittedAt()
        );
    }
}
