package org.zifeng.skilltree.compat;

import net.neoforged.fml.ModList;

import java.lang.reflect.Method;

/**
 * Just Enough Characters（JEC）兼容层 —— 让技能树搜索框支持拼音搜索（★ 2026-10-11）。
 *
 * <h2>为什么是「兼容 JEC」而不是自己做拼音</h2>
 * 本模组 2026-10-11 早些时候曾内置过一张离线的技能名拼音表（由 {@code tools} 脚本生成）。
 * 改走 JEC 有四个明确好处：
 * <ol>
 *   <li><b>多音字不再靠我们自己猜</b> —— JEC 的 PinIn 引擎有完整词典与多音字规则；</li>
 *   <li><b>支持全拼 / 声母 / 声调 / 注音 / 双拼</b>，且跟随玩家在 JEC 里的配置，</li>
 *       我们不必为每种输入法各维护一张表；</li>
 *   <li><b>与游戏内其他搜索框行为一致</b> —— 玩家装了 JEC，JEI / 创造模式背包都用它，</li>
 *       技能树跟着走才不会「同一个实例里两种手感」；</li>
 *   <li><b>不背词典资源</b> —— 词典在 JEC 里，我们零体积、零更新负担。</li>
 * </ol>
 *
 * <h2>为什么用反射而不是直接调用</h2>
 * JEC 是<b>可选前置</b>，直接引用 {@code Match} 会让本模组在未装 JEC 时也要求它存在于编译期
 * （要把 JEC 的 jar 放进 {@code libs/}，而 JEC 是 coremod，还牵着 PinIn / JEI 一串依赖）。
 * 反射只依赖一个类名 + 一个方法签名，两个平台完全同构，且<b>缺 JEC 时优雅降级</b>。
 *
 * <h2>调用的 API</h2>
 * <pre>
 *   // me.towdium.jecharacters.utils.Match（JEC 官方对外匹配入口）
 *   public static boolean matches(String text, String query)
 * </pre>
 * 参数顺序取自 JEC 自身用法：它给 JEI 打补丁时就是
 * {@code Match.matches(name, test)}（text 在前、query 在后），见其 {@code _lib.js} 与
 * {@code Match#matcher(Pattern test, CharSequence name)}。
 *
 * <h2>⚠️ 不额外做「语言判断」</h2>
 * 早先的自建实现要在中文语言下才启用拼音，因为那张表是按<b>中文技能名</b>生成的，
 * 英文界面下也会命中（把英文玩家搞糊涂）。走 JEC 后这个顾虑自动消失：
 * 匹配的文本就是界面上真正显示的名字 —— 英文语言下名字是英文，拼音自然匹配不到。
 * 因此<b>多一层语言判断反而是错的</b>（会与 JEC 在该实例里的其他搜索框行为不一致）。
 *
 * <p>⚠️ JEC 只在客户端安装（{@code @Mod.EventBusSubscriber(bus = MOD)} + 客户端搜索类），
 * 故本类只在客户端代码（技能树界面）里使用。
 */
public final class JecCompat {
    private JecCompat() {
    }

    /** JEC 的 modId */
    public static final String MOD_ID = "jecharacters";

    /** JEC 对外匹配入口的全限定名 */
    private static final String MATCH_CLASS = "me.towdium.jecharacters.utils.Match";

    /** JEC 是否已安装（懒检查 + 缓存） */
    private static Boolean loaded = null;

    /** 是否已打印过「已接入」日志（一次性，避免每次按键刷屏） */
    private static boolean loggedActive = false;

    /** 反射到的 {@code matches(String, String)}；{@code null} = 尚未解析或解析失败 */
    private static Method matchesMethod = null;

    /**
     * 解析失败 / 调用抛异常时置位。
     * <p>一旦置位就<b>不再重试</b>：搜索框每次按键要跑 170 次匹配，
     * 若每次都抛 {@code NoSuchMethodException} 会严重拖慢输入。
     */
    private static boolean broken = false;

    /** JEC 是否已安装 */
    public static boolean isLoaded() {
        if (loaded == null) {
            try {
                loaded = ModList.get().isLoaded(MOD_ID);
            } catch (Throwable ignored) {
                loaded = false;
            }
        }
        return loaded;
    }

    /**
     * 判断 {@code query} 是否按拼音匹配上了 {@code text}。
     *
     * <p>参数含义与 JEC 一致：{@code text} = 被搜索的文本，{@code query} = 玩家输入。
     *
     * <p>无 JEC / 解析失败 / 调用异常 → 一律返回 {@code false}（静默降级，
     * 调用方还有普通的子串匹配兜底，不会出现「搜索完全失灵」）。
     */
    public static boolean matches(String text, String query) {
        if (text == null || query == null || query.isEmpty() || !isLoaded() || broken) {
            return false;
        }
        Method method = resolve();
        if (method == null) {
            return false;
        }
        try {
            Object result = method.invoke(null, text, query);
            if (result instanceof Boolean matched && matched) {
                logActiveOnce();
                return true;
            }
            return false;
        } catch (Throwable ignored) {
            broken = true; // 永久关闭，避免每次按键都抛异常
            return false;
        }
    }

    /**
     * 一次性日志：第一次真正靠拼音匹配上时打印。
     *
     * <p>为什么挂在「匹配成功」而不是「加载时」：只有匹配成功才同时证明了三件事 ——
     * JEC 已安装、{@code Match} 类与方法都解析到了、反射调用真的按预期返回了 true。
     * 加载时打日志只能证明第一件。
     */
    private static void logActiveOnce() {
        if (!loggedActive) {
            loggedActive = true;
            org.slf4j.LoggerFactory.getLogger("zifeng_s_custom_skill_tree")
                    .info("[zifeng] 技能树搜索已接入 Just Enough Characters（拼音搜索生效）");
        }
    }

    /** 解析并缓存 {@code Match.matches(String, String)} */
    private static Method resolve() {
        Method cached = matchesMethod;
        if (cached != null) {
            return cached;
        }
        if (broken) {
            return null;
        }
        try {
            Class<?> clazz = Class.forName(MATCH_CLASS);
            Method method = clazz.getMethod("matches", String.class, String.class);
            matchesMethod = method;
            return method;
        } catch (Throwable ignored) {
            broken = true; // JEC 换了 API 或类名 → 关掉即可，不影响其他匹配方式
            return null;
        }
    }
}
