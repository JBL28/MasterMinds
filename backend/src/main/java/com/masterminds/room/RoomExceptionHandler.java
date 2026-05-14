package com.masterminds.room;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class RoomExceptionHandler {

    @ExceptionHandler(RoomNotFoundException.class)
    public ProblemDetail handleRoomNotFound(RoomNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setTitle("Room not found");
        problem.setDetail(exception.getMessage());
        return problem;
    }

    @ExceptionHandler(RoomRuleException.class)
    public ProblemDetail handleRoomRule(RoomRuleException exception) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Room rule violation");
        problem.setDetail(exception.getMessage());
        return problem;
    }
}
