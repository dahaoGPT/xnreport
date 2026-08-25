package com.xn.report.output;

/**
 * 输出文件重名冲突处理策略枚举。
 * <p>
 * 定义当输出目标文件在目标目录中已存在时的解决策略：
 * <ul>
 *   <li>{@link #FAIL}：抛出 {@code OUT-002} 异常终止执行。</li>
 *   <li>{@link #OVERWRITE}：安全覆盖已存在的文件（发布前先建立原子备份，并在失败时自动回滚）。</li>
 *   <li>{@link #VERSIONED}：自动追加版本序号后缀（如 {@code -1.xlsx}、{@code -1.docx}），生成全新文件。</li>
 * </ul>
 * </p>
 */
public enum CollisionPolicy {

    /** 冲突时直接抛出异常失败。 */
    FAIL,

    /** 冲突时覆盖已有同名文件。 */
    OVERWRITE,

    /** 冲突时自动追加版本序号生成新文件（默认策略）。 */
    VERSIONED
}
