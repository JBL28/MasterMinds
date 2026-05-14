package com.masterminds.chat;

import java.util.List;

public record DayMessageResult(
        boolean revealed,
        List<DayMessage> messages
) {
}
