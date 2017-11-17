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

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class StatsHolder {

    private long numWithPtGreaterThan1h;
    private long numWithPtGreaterThan6h;
    private long numWithConnGreaterThan50;

    private long numWithLastLoginMoreThan1moAgo;
    private long numWithLastLoginMoreThan1wAgo;
    private long numWithConnLessThan10;
    private long numWithPtLessThan30m;

    private int averageTimePlayed;
    private int averageTimesConnected;


    private long uniqueJoins;
    private long totalTimePlayed;
    private long totalConnections;

    private long uniqueJoinsMonth;
    private long newPlayersMonth;
    private long returningPlayersMonth;

    private long uniqueJoinsWeek;
    private long newPlayersWeek;
    private long returningPlayersWeek;

    private long uniqueJoinsToday;
    private long newPlayersToday;
    private long returningPlayersToday;

}
