/*
 * This file is part of NetworkAnalytics, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package me.lucko.networkanalytics;

import lombok.RequiredArgsConstructor;

import com.google.common.collect.Maps;

import me.lucko.helper.Scheduler;
import me.lucko.helper.utils.TimeUtil;
import me.lucko.networkanalytics.model.AnalyticsData;
import me.lucko.networkanalytics.model.OnlinePlayerRecord;
import me.lucko.networkanalytics.model.StatsHolder;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import protocolsupport.api.ProtocolVersion;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static me.lucko.helper.utils.Color.colorize;

@RequiredArgsConstructor
public class AnalyticsCommand implements CommandExecutor {
    private final AnalyticsPlugin plugin;


    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("networkanalytics.view")) {
            sender.sendMessage(colorize("&3[ANALYTICS] &fYou do not have permission to use this command."));
            return true;
        }

        sender.sendMessage(colorize("&3[ANALYTICS] &fRetrieving monitoring data..."));

        plugin.getDataManager().getStats().thenAcceptAsync(statsHolder -> {
            if (!statsHolder.isPresent()) {
                sender.sendMessage(colorize("&3[ANALYTICS] &fUnable to retrieve monitoring data."));
                return;
            }
            StatsHolder s = statsHolder.get();

            long all = s.getUniqueJoins();
            BigDecimal total = BigDecimal.valueOf(all);

            Map<ProtocolVersion, AtomicInteger> versionMap = new HashMap<>();

            for (AnalyticsData data : plugin.getAnalyticsDataMap().values()) {
                for (OnlinePlayerRecord p : data.getPlayers()) {
                    ProtocolVersion version = p.getVersion().orElse(null);
                    versionMap.computeIfAbsent(version, v -> new AtomicInteger(0)).incrementAndGet();
                }
            }

            List<Map.Entry<ProtocolVersion, Integer>> counts = versionMap.entrySet().stream()
                    .filter(e -> e.getValue().get() > 0)
                    .sorted((o1, o2) -> {
                        int i = Integer.compare(o2.getValue().get(), o1.getValue().get());
                        return i == 0 ? 1 : i;
                    })
                    .map(e -> Maps.immutableEntry(e.getKey(), e.getValue().get()))
                    .collect(Collectors.toList());

            BigDecimal playersWithVersion = BigDecimal.valueOf(counts.stream().mapToInt(Map.Entry::getValue).sum());

            sender.sendMessage(colorize("&f&m-&3&m-&f&m-&3&m-&f&m-&3&m-&f&m-&3&m-&f&m-&f[ &bAnalytics &f]&f&m-&3&m-&f&m-&3&m-&f&m-&3&m-&f&m-&3&m-&f&m-&r\n&r"));
            sender.sendMessage(colorize("&fPlayer Retention:"));
            sender.sendMessage(colorize("  &3- &fPlay time greater than 1h: &3" + formatPercent(total, s.getNumWithPtGreaterThan1h())));
            sender.sendMessage(colorize("  &3- &fPlay time greater than 6h: &3" + formatPercent(total, s.getNumWithPtGreaterThan6h())));
            sender.sendMessage(colorize("  &3- &fConnected more than 50 times: &3" + formatPercent(total, s.getNumWithConnGreaterThan50())));
            sender.sendMessage(" ");
            sender.sendMessage(colorize("  &3- &fLast login more than 1 month ago: &3" + formatPercent(total, s.getNumWithLastLoginMoreThan1moAgo())));
            sender.sendMessage(colorize("  &3- &fLast login more than 1 week ago: &3" + formatPercent(total, s.getNumWithLastLoginMoreThan1wAgo())));
            sender.sendMessage(colorize("  &3- &fConnected less than 10 times: &3" + formatPercent(total, s.getNumWithConnLessThan10())));
            sender.sendMessage(colorize("  &3- &fPlay time less than 30 minutes: &3" + formatPercent(total, s.getNumWithPtLessThan30m())));
            sender.sendMessage(" ");
            sender.sendMessage(colorize("  &3- &fAverage time played: &3" + TimeUtil.toShortForm(s.getAverageTimePlayed() * 60L)));
            sender.sendMessage(colorize("  &3- &fAverage times connected: &3" + s.getAverageTimesConnected()));
            sender.sendMessage(" ");
            sender.sendMessage(colorize("&fAll time:"));
            sender.sendMessage(colorize("  &3- &fUnique joins: &3" + formatNumberShort(s.getUniqueJoins())));
            sender.sendMessage(colorize("  &3- &fTotal time played: &3" + TimeUtil.toShortForm(s.getTotalTimePlayed() * 60L)));
            sender.sendMessage(colorize("  &3- &fTotal connections: &3" + formatNumberShort(s.getTotalConnections())));
            sender.sendMessage(" ");
            sender.sendMessage(colorize("&fLast month:"));
            sender.sendMessage(colorize("  &3- &fUnique joins: &3" + formatNumberShort(s.getUniqueJoinsMonth())));
            sender.sendMessage(colorize("  &3- &fNew players: &3" + formatNumberShort(s.getNewPlayersMonth()) + " &7(" + formatPercent(BigDecimal.valueOf(s.getUniqueJoinsMonth()), s.getNewPlayersMonth()) + "&7)"));
            sender.sendMessage(colorize("  &3- &fReturning players: &3" + formatNumberShort(s.getReturningPlayersMonth()) + " &7(" + formatPercent(BigDecimal.valueOf(s.getUniqueJoinsMonth()), s.getReturningPlayersMonth()) + "&7)"));
            sender.sendMessage(colorize("&fLast week:"));
            sender.sendMessage(colorize("  &3- &fUnique joins: &3" + formatNumberShort(s.getUniqueJoinsWeek())));
            sender.sendMessage(colorize("  &3- &fNew players: &3" + formatNumberShort(s.getNewPlayersWeek()) + " &7(" + formatPercent(BigDecimal.valueOf(s.getUniqueJoinsWeek()), s.getNewPlayersWeek()) + "&7)"));
            sender.sendMessage(colorize("  &3- &fReturning players: &3" + formatNumberShort(s.getReturningPlayersWeek()) + " &7(" + formatPercent(BigDecimal.valueOf(s.getUniqueJoinsWeek()), s.getReturningPlayersWeek()) + "&7)"));
            sender.sendMessage(colorize("&fLast 24 hours:"));
            sender.sendMessage(colorize("  &3- &fUnique joins: &3" + formatNumberShort(s.getUniqueJoinsToday())));
            sender.sendMessage(colorize("  &3- &fNew players: &3" + formatNumberShort(s.getNewPlayersToday()) + " &7(" + formatPercent(BigDecimal.valueOf(s.getUniqueJoinsToday()), s.getNewPlayersToday()) + "&7)"));
            sender.sendMessage(colorize("  &3- &fReturning players: &3" + formatNumberShort(s.getReturningPlayersToday()) + " &7(" + formatPercent(BigDecimal.valueOf(s.getUniqueJoinsToday()), s.getReturningPlayersToday()) + "&7)"));
            sender.sendMessage(" ");
            sender.sendMessage(colorize("&fPlayer Versions:"));
            for (Map.Entry<ProtocolVersion, Integer> versionData : counts) {
                sender.sendMessage(colorize("  &3- &f" + AnalyticsPlugin.getProtocolName(versionData.getKey()) + ": &3" + versionData.getValue() + " &7(" + formatPercent(playersWithVersion, versionData.getValue()) + ")"));
            }
            sender.sendMessage(" ");
        }, Scheduler.async());
        return true;
    }

    private static String formatPercent(BigDecimal total, long quot) {
        return BigDecimal.valueOf(quot).multiply(BigDecimal.valueOf(100)).divide(total, BigDecimal.ROUND_HALF_UP).round(new MathContext(3, RoundingMode.HALF_UP)).toPlainString() + "%";
    }

    private static String formatNumberShort(long num) {
        if (num >= 1000000000000000L) {
            return (Math.floor(((float) num / 1000000000000000f) * 10f) / 10d) + "Q";
        }
        if (num >= 1000000000000L) {
            return (Math.floor(((float) num / 1000000000000f) * 10f) / 10d) + "T";
        }
        if (num >= 1000000000L) {
            return (Math.floor(((float) num / 1000000000f) * 10f) / 10d) + "B";
        }
        if (num >= 1000000) {
            return (Math.floor(((float) num / 1000000L) * 10f) / 10d) + "M";
        }
        return NumberFormat.getNumberInstance(Locale.US).format(num);
    }
}
