package com.exambattle.common.protocol.bank;

/**
 * ADD_QUESTION（examId、question）與 UPDATE_QUESTION（examId、questionId、question）。
 */
public record QuestionRequest(Long examId, Long questionId, QuestionData question) {
}
