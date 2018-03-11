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

package me.lucko.networkanalytics.data;

import lombok.RequiredArgsConstructor;

import me.lucko.helper.Schedulers;
import me.lucko.helper.sql.HelperDataSource;
import me.lucko.networkanalytics.AnalyticsPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
public class DataManager {

    private static final String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS `analytics_data` (`uuid` VARCHAR(36) NOT NULL, `username` VARCHAR(16) NOT NULL, `first_login` INT NOT NULL, `last_login` INT NOT NULL, `last_seen` VARCHAR(32) NOT NULL, `times_connected` INT NOT NULL, `minutes_played` INT NOT NULL, PRIMARY KEY (`uuid`))";
    private static final String INSERT = "INSERT INTO analytics_data VALUES(?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE username=?, last_login=?, last_seen=?, times_connected = times_connected + 1";
    private static final String UPDATE_MINUTES = "UPDATE analytics_data SET minutes_played = minutes_played + ? WHERE uuid=?";
    private static final String SELECT = "SELECT * FROM analytics_data WHERE uuid=?";
    private static final String SELECT_UUID = "SELECT uuid FROM analytics_data WHERE username=?";
    private static final String SELECT_USERNAME = "SELECT username FROM analytics_data WHERE uuid=?";

    private final AnalyticsPlugin plugin;
    private final HelperDataSource sql;

    public void init() {
        try (Connection c = sql.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(CREATE_TABLE)) {
                ps.execute();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public CompletableFuture<Void> logPlayer(UUID uuid, String username) {
        return CompletableFuture.runAsync(() -> {
            long time = System.currentTimeMillis() / 1000L;
            try (Connection c = sql.getConnection()) {
                try (PreparedStatement ps = c.prepareStatement(INSERT)) {
                    // insert
                    ps.setString(1, uuid.toString()); // uuid
                    ps.setString(2, username); // username
                    ps.setLong(3, time); // first login
                    ps.setLong(4, time); // last login
                    ps.setString(5, plugin.getInstanceId()); // last seen
                    ps.setLong(6, 1); // times connected
                    ps.setLong(7, 0); // minutes played

                    // update
                    ps.setString(8, username); // username
                    ps.setLong(9, time); // last login
                    ps.setString(10, plugin.getInstanceId()); // last seen

                    ps.execute();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }, Schedulers.async());
    }

    public CompletableFuture<Boolean> incrementPlayerMinutesPlayed(UUID uuid, int minutes) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection c = sql.getConnection()) {
                try (PreparedStatement ps = c.prepareStatement(UPDATE_MINUTES)) {
                    ps.setInt(1, minutes);
                    ps.setString(2, uuid.toString());
                    ps.execute();
                    return true;
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return false;
        }, Schedulers.async());
    }

    public CompletableFuture<Optional<String>> getUsername(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection c = sql.getConnection()) {
                try (PreparedStatement ps = c.prepareStatement(SELECT_USERNAME)) {
                    ps.setString(1, uuid.toString());

                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            return Optional.of(rs.getString("username"));
                        }
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return Optional.empty();
        }, Schedulers.async());
    }

    public CompletableFuture<Optional<UUID>> getUuid(String username) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection c = sql.getConnection()) {
                try (PreparedStatement ps = c.prepareStatement(SELECT_UUID)) {
                    ps.setString(1, username);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            return Optional.of(UUID.fromString(rs.getString("uuid")));
                        }
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return Optional.empty();
        }, Schedulers.async());
    }

    public CompletableFuture<Optional<PlayerRecord>> getPlayerData(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection c = sql.getConnection()) {
                try (PreparedStatement ps = c.prepareStatement(SELECT)) {
                    ps.setString(1, uuid.toString());

                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            String username = rs.getString("username");
                            long firstLogin = rs.getLong("first_login");
                            long lastLogin = rs.getLong("last_login");
                            String lastSeen = rs.getString("last_seen");
                            int timesConnected = rs.getInt("times_connected");
                            int minutesPlayed = rs.getInt("minutes_played");

                            return Optional.of(new PlayerRecord(uuid, username, firstLogin, lastLogin, lastSeen, timesConnected, minutesPlayed));
                        }
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return Optional.empty();
        }, Schedulers.async());
    }

    public CompletableFuture<Optional<StatsHolder>> getStats() {
        return CompletableFuture.supplyAsync(() -> {
            long now = System.currentTimeMillis() / 1000L;

            long month = now - 2592000L;
            long week = now - 604800L;
            long day = now - 86400;

            try (Connection c = sql.getConnection()) {
                long numWithPtGreaterThan1h = getLong(c, "SELECT COUNT(*) AS amount FROM analytics_data WHERE minutes_played > ?", 60);
                long numWithPtGreaterThan6h = getLong(c, "SELECT COUNT(*) AS amount FROM analytics_data WHERE minutes_played > ?", 360);
                long numWithConnGreaterThan50 = getLong(c, "SELECT COUNT(*) AS amount FROM analytics_data WHERE times_connected > ?", 50);

                long numWithLastLoginMoreThan1moAgo = getLong(c, "SELECT COUNT(*) AS amount FROM analytics_data WHERE last_login < ?", month);
                long numWithLastLoginMoreThan1wAgo = getLong(c, "SELECT COUNT(*) AS amount FROM analytics_data WHERE last_login < ?", week);
                long numWithConnLessThan10 = getLong(c, "SELECT COUNT(*) AS amount FROM analytics_data WHERE times_connected < ?", 10);
                long numWithPtLessThan30m = getLong(c, "SELECT COUNT(*) AS amount FROM analytics_data WHERE minutes_played < ?", 30);

                int averageTimePlayed = getInt(c, "SELECT AVG(minutes_played) AS amount FROM analytics_data");
                int averageTimesConnected = getInt(c, "SELECT AVG(times_connected) AS amount FROM analytics_data");

                long uniqueJoins = getLong(c, "SELECT COUNT(*) AS amount FROM analytics_data");
                long totalTimePlayed = getLong(c, "SELECT sum(minutes_played) AS amount FROM analytics_data");
                long totalConnections = getLong(c, "SELECT sum(times_connected) AS amount FROM analytics_data");

                long uniqueJoinsMonth = getLong(c, "SELECT COUNT(*) AS amount FROM analytics_data WHERE lASt_login > ?", month);
                long newPlayersMonth = getLong(c, "SELECT COUNT(*) AS amount FROM analytics_data WHERE first_login > ?", month);
                long returningPlayersMonth = uniqueJoinsMonth - newPlayersMonth;

                long uniqueJoinsWeek = getLong(c, "SELECT COUNT(*) AS amount FROM analytics_data WHERE lASt_login > ?", week);
                long newPlayersWeek = getLong(c, "SELECT COUNT(*) AS amount FROM analytics_data WHERE first_login > ?", week);
                long returningPlayersWeek = uniqueJoinsWeek - newPlayersWeek;

                long uniqueJoinsToday = getLong(c, "SELECT COUNT(*) AS amount FROM analytics_data WHERE lASt_login > ?", day);
                long newPlayersToday = getLong(c, "SELECT COUNT(*) AS amount FROM analytics_data WHERE first_login > ?", day);
                long returningPlayersToday = uniqueJoinsToday - newPlayersToday;

                StatsHolder holder = new StatsHolder(
                        numWithPtGreaterThan1h, numWithPtGreaterThan6h, numWithConnGreaterThan50,
                        numWithLastLoginMoreThan1moAgo, numWithLastLoginMoreThan1wAgo, numWithConnLessThan10, numWithPtLessThan30m,
                        averageTimePlayed, averageTimesConnected,
                        uniqueJoins, totalTimePlayed, totalConnections,
                        uniqueJoinsMonth, newPlayersMonth, returningPlayersMonth,
                        uniqueJoinsWeek, newPlayersWeek, returningPlayersWeek,
                        uniqueJoinsToday, newPlayersToday, returningPlayersToday
                );

                return Optional.of(holder);
            } catch (Exception e) {
                e.printStackTrace();
            }

            return Optional.empty();
        }, Schedulers.async());
    }

    private static long getLong(Connection c, String query) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(query)) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("amount");
                }
            }
        } catch (SQLException e) {
            throw new IllegalArgumentException(query, e);
        }
        throw new IllegalArgumentException(query);
    }

    private static long getLong(Connection c, String query, long val) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(query)) {
            ps.setLong(1, val);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("amount");
                }
            }
        } catch (SQLException e) {
            throw new IllegalArgumentException(query, e);
        }
        throw new IllegalArgumentException(query);
    }

    private static int getInt(Connection c, String query) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(query)) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return (int) rs.getDouble("amount");
                }
            }
        } catch (SQLException e) {
            throw new IllegalArgumentException(query, e);
        }
        throw new IllegalArgumentException(query);
    }
}
