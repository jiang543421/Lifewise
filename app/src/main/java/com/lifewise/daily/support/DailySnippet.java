package com.lifewise.daily.support;

import java.util.Optional;

/**
 * 日报内容截取（120 字符 + …），跨模块快照与本地摘要列表共用。
 *
 * <p>设计要点：
 * <ul>
 *   <li>不可变、纯函数，无副作用</li>
 *   <li>输入 null 返回 null（避免 NPE）</li>
 *   <li>120 字符上限来自 {@code DailySnapshot.summary} 字段语义约定（plan-02-daily §4）</li>
 * </ul>
 */
public final class DailySnippet {

    public static final int MAX_LENGTH = 120;

    private DailySnippet() {
        // utility class
    }

    public static String of(String content) {
        return Optional.ofNullable(content)
                .filter(c -> c.length() <= MAX_LENGTH)
                .orElseGet(() -> content == null ? null
                        : content.substring(0, MAX_LENGTH) + "…");
    }
}
