package com.masterminds.chat.dto;

import java.util.List;

public record DayMessageSubmissionResponse(
        boolean revealed,
        List<DayMessageResponse> messages
) {
}
