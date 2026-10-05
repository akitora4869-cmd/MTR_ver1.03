package Rin.TRPGCharacter;

import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

public class CompositeSkillManager {

    private final Plugin plugin;
    private final CharacterManager characterManager;
    private final SkillManager skillManager;
    private final Random random = new Random();
    // 手動で2技能を続けて振った場合も複合技能として扱う猶予時間。
    private static final long MANUAL_GRACE_MS = 12_000L;
    private final Map<UUID, PendingManualRoll> pendingManual = new HashMap<>();

    private record PendingManualRoll(String skillId, CheckResult result, long at) {}

    /**
     * 複合技能の技名本体。修飾語は技能側から自動生成する。
     * 新しい複合技能を増やす場合、基本的にはここへ技名を1つ追加するだけでよい。
     */
    private String baseTechniqueName(String comboId) {
        return switch (comboId) {
            case "medicine_firstaid" -> "治療";
            case "spot_listen" -> "索敵";
            case "hide_sneak" -> "潜行";
            case "climb_jump" -> "機動";
            case "jump_kick" -> "飛び蹴り";
            default -> "複合技能";
        };
    }

    /**
     * 技能が複合判定へ成功した時に付与する修飾語。
     * comboIdには依存させず「どの技能が成功したか」から生成するため、
     * 同じ技能を別の複合技能へ使っても自動的に同系統の二つ名が付く。
     */
    private String modifierForSkill(String skillId) {
        return switch (skillId.toLowerCase()) {
            case "medicine" -> "精密な";
            case "first_aid" -> "手際のよい";
            case "spot_hidden" -> "鋭い";
            case "listen" -> "鋭敏な";
            case "hide" -> "影のような";
            case "sneak" -> "静かな";
            case "climb" -> "力強い";
            case "jump" -> "軽快な";
            case "kick" -> "鋭い";
            case "martial_arts" -> "強力な";
            case "psychology" -> "洞察に満ちた";
            case "persuade", "fast_talk" -> "巧みな";
            case "library_use" -> "緻密な";
            case "track" -> "執拗な";
            default -> "";
        };
    }

    /** クリティカルした技能は通常修飾語より一段強い共通表現に昇格する。 */
    private String criticalModifierForSkill(String skillId) {
        return switch (skillId.toLowerCase()) {
            case "martial_arts" -> "達人級の";
            case "hide", "sneak" -> "完全な";
            case "spot_hidden", "listen" -> "研ぎ澄まされた";
            case "medicine", "first_aid" -> "卓越した";
            case "climb", "jump" -> "華麗な";
            default -> "卓越した";
        };
    }

    private record SkillOutcome(String skillId, CheckResult result) {}

    /**
     * 成功した技能から自動的に「修飾語＋技名」を作る。
     * クリティカル > 補助技能(MA等) > 構成技能の順で優先し、表示が長くなり過ぎないよう
     * 原則1つ、性質の異なる補助修飾がある場合のみ最大2つまで採用する。
     */
    private String autoTechniqueName(String comboId, SkillOutcome... outcomes) {
        String base = baseTechniqueName(comboId);
        String critical = null;
        String auxiliary = null;
        String normal = null;

        for (SkillOutcome outcome : outcomes) {
            if (outcome == null || outcome.result() == null || !outcome.result().isSuccess()) continue;
            String id = outcome.skillId();
            if (outcome.result() == CheckResult.CRITICAL) {
                critical = criticalModifierForSkill(id);
                continue;
            }
            String modifier = modifierForSkill(id);
            if (modifier.isBlank()) continue;
            if ("martial_arts".equalsIgnoreCase(id)) auxiliary = modifier;
            else if (normal == null) normal = modifier;
        }

        if (critical != null) return critical + base;
        if (auxiliary != null && normal != null && !auxiliary.equals(normal)) return auxiliary + normal + base;
        if (auxiliary != null) return auxiliary + base;
        if (normal != null) return normal + base;
        return base;
    }

    private String resolvedTechniqueName(String comboId, CheckResult a, CheckResult b) {
        String[] info = comboInfo(comboId);
        if (info == null) return baseTechniqueName(comboId);
        return autoTechniqueName(comboId,
                new SkillOutcome(info[1], a),
                new SkillOutcome(info[3], b));
    }

    public CompositeSkillManager(Plugin plugin,
                                 CharacterManager characterManager,
                                 SkillManager skillManager) {
        this.plugin = plugin;
        this.characterManager = characterManager;
        this.skillManager = skillManager;
    }

    public void roll(Player player, String comboId) {
        switch (comboId) {
            case "medicine_firstaid" ->
                    rollPair(player, comboId, "医学", "medicine", "応急手当", "first_aid");
            case "spot_listen" ->
                    rollPair(player, comboId, "目星", "spot_hidden", "聞き耳", "listen");
            case "hide_sneak" ->
                    rollPair(player, comboId, "隠れる", "hide", "忍び歩き", "sneak");
            case "climb_jump" ->
                    rollPair(player, comboId, "登攀", "climb", "跳躍", "jump");
            case "jump_kick" ->
                    rollPair(player, comboId, "跳躍", "jump", "キック", "kick");
            default -> player.sendMessage(color("&c複合技能が見つかりません。"));
        }
    }


    /**
     * 通常の技能判定を連続して行った場合に、既知の組み合わせなら自動で複合判定へまとめる。
     * 先に振った技能は10秒間保持されるため、GUI/本/コマンドのどこから振ってもよい。
     */
    /** 直前の技能と複合できる2個目の技能だけは、共通CT中でも判定を許可する。 */
    public boolean isManualContinuation(Player player, String skillId) {
        PendingManualRoll prev = pendingManual.get(player.getUniqueId());
        if (prev == null) return false;
        if (System.currentTimeMillis() - prev.at() > MANUAL_GRACE_MS) {
            pendingManual.remove(player.getUniqueId());
            return false;
        }
        return findCombo(prev.skillId(), skillId) != null;
    }

    public void recordManualResult(Player player, String skillId, CheckResult result) {
        long now = System.currentTimeMillis();
        PendingManualRoll prev = pendingManual.get(player.getUniqueId());
        if (prev != null && now - prev.at() <= MANUAL_GRACE_MS) {
            String comboId = findCombo(prev.skillId(), skillId);
            if (comboId != null) {
                pendingManual.remove(player.getUniqueId());
                String[] info = comboInfo(comboId);
                player.sendMessage(color("&6[複合技能] &e手動判定を結合しました &7(" + info[0] + "＋" + info[2] + ")"));
                applyResult(player, comboId, prev.result(), result);
                return;
            }
        }
        pendingManual.put(player.getUniqueId(), new PendingManualRoll(skillId, result, now));
    }

    private String findCombo(String a, String b) {
        String[][] combos = {
                {"medicine_firstaid", "medicine", "first_aid"},
                {"spot_listen", "spot_hidden", "listen"},
                {"hide_sneak", "hide", "sneak"},
                {"climb_jump", "climb", "jump"},
                {"jump_kick", "jump", "kick"}
        };
        for (String[] c : combos) {
            if ((c[1].equalsIgnoreCase(a) && c[2].equalsIgnoreCase(b))
                    || (c[2].equalsIgnoreCase(a) && c[1].equalsIgnoreCase(b))) return c[0];
        }
        return null;
    }

    private String[] comboInfo(String comboId) {
        return switch (comboId) {
            case "medicine_firstaid" -> new String[]{"医学", "medicine", "応急手当", "first_aid"};
            case "spot_listen" -> new String[]{"目星", "spot_hidden", "聞き耳", "listen"};
            case "hide_sneak" -> new String[]{"隠れる", "hide", "忍び歩き", "sneak"};
            case "climb_jump" -> new String[]{"登攀", "climb", "跳躍", "jump"};
            case "jump_kick" -> new String[]{"跳躍", "jump", "キック", "kick"};
            default -> null;
        };
    }

    private void rollPair(Player player,
                          String comboId,
                          String nameA,
                          String skillA,
                          String nameB,
                          String skillB) {
        int targetA = skillManager.getSkillValue(player, skillA);
        int targetB = skillManager.getSkillValue(player, skillB);

        plugin.getDiceSoundManager().playRollSequence(player, () -> {
            int rollA = random.nextInt(100) + 1;
            int rollB = random.nextInt(100) + 1;

            CheckResult resultA = CheckResult.evaluate(rollA, targetA);
            CheckResult resultB = CheckResult.evaluate(rollB, targetB);

            plugin.getSkillGrowthManager().tryGrowth(
                    player, skillA, nameA, resultA
            );
            plugin.getSkillGrowthManager().tryGrowth(
                    player, skillB, nameB, resultB
            );

            boolean successA = resultA.isSuccess();
            boolean successB = resultB.isSuccess();

            player.sendMessage(color("&6[複合技能] &f" + nameA + "＋" + nameB));
            player.sendMessage(color("&b" + nameA + " &f" + rollA + " / " + targetA
                    + " &7→ " + resultA.color() + resultA.label()));
            player.sendMessage(color("&b" + nameB + " &f" + rollB + " / " + targetB
                    + " &7→ " + resultB.color() + resultB.label()));

            plugin.getDiceSoundManager().playResultSound(
                    player,
                    chooseCompositeSound(resultA, resultB)
            );

            applyResult(player, comboId, resultA, resultB);
        });
    }

    private CheckResult chooseCompositeSound(CheckResult a, CheckResult b) {
        if (a == CheckResult.FUMBLE || b == CheckResult.FUMBLE) {
            return CheckResult.FUMBLE;
        }
        if (a == CheckResult.CRITICAL || b == CheckResult.CRITICAL) {
            return CheckResult.CRITICAL;
        }
        if (a == CheckResult.SPECIAL || b == CheckResult.SPECIAL) {
            return CheckResult.SPECIAL;
        }
        if (a.isSuccess() || b.isSuccess()) {
            return CheckResult.SUCCESS;
        }
        return CheckResult.FAILURE;
    }

    private void applyResult(Player player, String comboId, CheckResult resultA, CheckResult resultB) {
        boolean a = resultA.isSuccess();
        boolean b = resultB.isSuccess();
        if (!a && !b) {
            player.sendMessage(color("&c結果: 両方失敗"));
            return;
        }

        // 成功度に応じて、複合技能を単なる「A+B」ではなく技名として見せる。
        // 飛び蹴りはMAの成否で最終名が決まるため、専用処理側で表示する。
        if (!"jump_kick".equals(comboId)) {
            String technique = resolvedTechniqueName(comboId, resultA, resultB);
            player.sendMessage(color("&6[複合技能] &e" + technique));
        }

        switch (comboId) {
            case "medicine_firstaid" -> medicalEffect(player, a, b);
            case "spot_listen" -> perceptionEffect(player, a, b);
            case "hide_sneak" -> stealthEffect(player, a, b);
            case "climb_jump" -> movementEffect(player, a, b);
            case "jump_kick" -> flyingKickEffect(player, resultA, resultB);
        }
    }

    private void medicalEffect(Player player, boolean medicine, boolean firstAid) {
        int amount;

        if (medicine && firstAid) {
            amount = rollDice(2, 3) + 1;
            player.sendMessage(color("&a結果: 両方成功 → HP 2d3+1 回復"));
        } else if (medicine) {
            amount = rollDice(1, 3) + 1;
            player.sendMessage(color("&a結果: 医学のみ成功 → HP 1d3+1 回復"));
        } else {
            amount = rollDice(1, 3);
            player.sendMessage(color("&a結果: 応急手当のみ成功 → HP 1d3 回復"));
        }

        int before = characterManager.getCurrentHp(player);
        int maxHp = characterManager.getHp(player);
        int after = Math.min(maxHp, before + amount);
        characterManager.setCurrentHp(player, after);

        player.sendMessage(color("&fHP " + before + " → " + after
                + " &7(+" + (after - before) + ")"));

        plugin.getHealthSyncManager().sync(player);
        plugin.getSidebarManager().updatePlayer(player);
    }

    private void perceptionEffect(Player player, boolean spot, boolean listen) {
        if (spot && listen) {
            player.sendMessage(color("&a結果: 両方成功 → 広範囲の気配を強調"));
            glowNearby(player, 24.0, 20L * 15L);
            reportNearby(player, 24.0);
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.2f);
        } else if (spot) {
            player.sendMessage(color("&a結果: 目星のみ成功 → 周囲を強調"));
            glowNearby(player, 16.0, 20L * 8L);
        } else {
            player.sendMessage(color("&a結果: 聞き耳のみ成功 → 周囲の気配を通知"));
            reportNearby(player, 24.0);
        }
    }

    private void stealthEffect(Player player, boolean hide, boolean sneak) {
        if (hide && sneak) {
            player.sendMessage(color("&a結果: 両方成功 → 20秒間、透明化＋移動補助"));
            addPotion(player, PotionEffectType.INVISIBILITY, 20 * 20, 0);
            addPotion(player, PotionEffectType.SPEED, 20 * 20, 0);
        } else if (hide) {
            player.sendMessage(color("&a結果: 隠れるのみ成功 → 10秒間透明化"));
            addPotion(player, PotionEffectType.INVISIBILITY, 20 * 10, 0);
        } else {
            player.sendMessage(color("&a結果: 忍び歩きのみ成功 → 10秒間移動補助"));
            addPotion(player, PotionEffectType.SPEED, 20 * 10, 0);
        }
    }

    private void movementEffect(Player player, boolean climb, boolean jump) {
        if (climb && jump) {
            player.sendMessage(color("&a結果: 両方成功 → 20秒間、強力な移動補助"));
            addPotion(player, PotionEffectType.JUMP_BOOST, 20 * 20, 1);
            addPotion(player, PotionEffectType.SLOW_FALLING, 20 * 20, 0);
            addPotion(player, PotionEffectType.SPEED, 20 * 20, 0);
        } else if (climb) {
            player.sendMessage(color("&a結果: 登攀のみ成功 → 15秒間登攀補助"));
            addPotion(player, PotionEffectType.JUMP_BOOST, 20 * 15, 0);
            addPotion(player, PotionEffectType.SLOW_FALLING, 20 * 15, 0);
        } else {
            player.sendMessage(color("&a結果: 跳躍のみ成功 → 15秒間跳躍力上昇"));
            addPotion(player, PotionEffectType.JUMP_BOOST, 20 * 15, 1);
        }
    }


    private void flyingKickEffect(Player player, CheckResult jumpResult, CheckResult kickResult) {
        boolean jump = jumpResult.isSuccess();
        boolean kick = kickResult.isSuccess();
        if (!jump || !kick) {
            if (!jump && !kick) player.sendMessage(color("&c結果: 跳躍・キックともに失敗 → 飛び蹴り失敗"));
            else if (!jump) player.sendMessage(color("&e結果: キック成功 / 跳躍失敗 → 踏み切れず飛び蹴り失敗"));
            else player.sendMessage(color("&e結果: 跳躍成功 / キック失敗 → 攻撃を当てられなかった"));
            return;
        }

        // MAは初期値1より成長・割振りされている探索者だけ、自動で追加判定する。
        int maValue = skillManager.getSkillValue(player, "martial_arts");
        boolean hasMa = maValue > 1;
        boolean maSuccess = false;
        CheckResult maResult = null;
        if (hasMa) {
            int maRoll = random.nextInt(100) + 1;
            maResult = CheckResult.evaluate(maRoll, maValue);
            maSuccess = maResult.isSuccess();
            SkillDefinition def = skillManager.getSkill("martial_arts");
            plugin.getSkillGrowthManager().tryGrowth(player, "martial_arts",
                    def != null ? def.getName() : "マーシャルアーツ", maResult);
            player.sendMessage(color("&d[MA追加判定] &fマーシャルアーツ &7" + maRoll + " / " + maValue
                    + " &7→ " + maResult.color() + maResult.label()));
        }

        Entity rawTarget = player.getTargetEntity(4);
        if (!(rawTarget instanceof LivingEntity target) || target == player) {
            player.sendMessage(color("&e飛び蹴りは成功しましたが、正面4ブロック以内に対象がいません。"));
            return;
        }

        int base = rollDice(1, 6);
        int db = rollDamageBonus(player);
        int subtotal = Math.max(0, base + db);
        int damage = maSuccess ? subtotal * 2 : subtotal;

        // 飛び蹴りの見た目。MA成功時だけ追加のクリティカル風演出を重ねる。
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation().add(0, 0.8, 0), 12, 0.25, 0.15, 0.25, 0.04);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.15f);
        String techniqueName = autoTechniqueName("jump_kick",
                new SkillOutcome("jump", jumpResult),
                new SkillOutcome("kick", kickResult),
                maSuccess ? new SkillOutcome("martial_arts", maResult) : null);
        if (maSuccess) {
            player.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1.0, 0), 18, 0.35, 0.45, 0.35, 0.12);
            player.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.2f);
            player.sendMessage(color("&d✦ " + techniqueName + "！ &f踏み込みから蹴撃までが鋭く繋がった！"));
        } else if (hasMa) {
            player.sendMessage(color("&a" + techniqueName + "成功！ &7MAは失敗したため追加強化なし"));
        } else {
            player.sendMessage(color("&a" + techniqueName + "成功！ &7(跳躍＋キック)"));
        }

        if (target instanceof Player targetPlayer && characterManager.hasConfiguredStats(targetPlayer)) {
            int before = characterManager.getCurrentHp(targetPlayer);
            int after = Math.max(0, before - damage);
            characterManager.setCurrentHp(targetPlayer, after);
            plugin.getHealthSyncManager().sync(targetPlayer);
            plugin.getSidebarManager().updatePlayer(targetPlayer);
        } else {
            target.setHealth(Math.max(0.0, target.getHealth() - damage));
        }

        target.setVelocity(target.getVelocity().add(player.getLocation().getDirection().normalize().multiply(maSuccess ? 0.75 : 0.45).setY(0.25)));
        player.sendMessage(color("&6[" + techniqueName + "] &f1d6(" + base + ") + DB(" + db + ")"
                + (maSuccess ? " &d×2(MA)" : "") + " &7→ &c" + damage + "ダメージ"));
    }

    private int rollDamageBonus(Player player) {
        int total = characterManager.getStat(player, "STR") + characterManager.getStat(player, "SIZ");
        if (total <= 12) return -rollDice(1, 6);
        if (total <= 16) return -rollDice(1, 4);
        if (total <= 24) return 0;
        if (total <= 32) return rollDice(1, 4);
        if (total <= 40) return rollDice(1, 6);
        int dice = 2 + Math.max(0, (total - 41) / 16);
        return rollDice(dice, 6);
    }

    private void glowNearby(Player player, double radius, long duration) {
        List<LivingEntity> targets = new ArrayList<>();

        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof LivingEntity living && entity != player) {
                targets.add(living);
                living.setGlowing(true);
            }
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (LivingEntity target : targets) {
                if (target.isValid()) {
                    target.setGlowing(false);
                }
            }
        }, duration);
    }

    private void reportNearby(Player player, double radius) {
        List<LivingEntity> targets = new ArrayList<>();

        for (Entity entity : player.getNearbyEntities(radius, radius / 2.0, radius)) {
            if (entity instanceof LivingEntity living && entity != player) {
                targets.add(living);
            }
        }

        if (targets.isEmpty()) {
            player.sendMessage(color("&e周囲に生物の気配はありません。"));
            return;
        }

        targets.sort(Comparator.comparingDouble(
                e -> e.getLocation().distanceSquared(player.getLocation())
        ));

        double distance = Math.sqrt(
                targets.get(0).getLocation().distanceSquared(player.getLocation())
        );

        player.sendMessage(color("&e気配: " + targets.size()
                + "体 / 最短 約" + Math.max(1, (int) Math.round(distance)) + "ブロック"));
    }

    private void addPotion(Player player, PotionEffectType type, int ticks, int amplifier) {
        player.addPotionEffect(new PotionEffect(type, ticks, amplifier, true, true, true));
    }

    private int rollDice(int count, int sides) {
        int total = 0;
        for (int i = 0; i < count; i++) {
            total += random.nextInt(sides) + 1;
        }
        return total;
    }

    private String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value);
    }
}
