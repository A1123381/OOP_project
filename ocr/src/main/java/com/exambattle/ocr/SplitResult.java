package com.exambattle.ocr;

import java.util.List;

/** 切題結果：題目依題號排列；{@link DraftQuestion#groupIndex()} 指向 groups 中的題組。 */
public record SplitResult(List<DraftGroup> groups, List<DraftQuestion> questions) {
}
