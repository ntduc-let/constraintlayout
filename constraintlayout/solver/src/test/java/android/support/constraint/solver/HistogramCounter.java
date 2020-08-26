/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package android.support.constraint.solver;

/**
 * Utility to draw an histogram
 */
public class HistogramCounter {
    long[] calls = new long[256];
    final String name;

    public void inc(int value) {
        if (value < 255) {
            calls[value]++;
        } else {
            calls[255]++;
        }
    }

    public HistogramCounter(String name) {
        this.name = name;
    }

    public void reset() {
        calls = new long[256];
    }

    private String print(long n) {
        String ret = "";
        for (int i = 0; i < n; i++) {
            ret += "X";
        }
        return ret;
    }

    @Override
    public String toString() {
        String ret = name + " :\n";
        int lastValue = 255;
        for (int i = 255; i >= 0; i--) {
            if (calls[i] != 0) {
                lastValue = i;
                break;
            }
        }
        int total = 0;
        for (int i = 0; i <= lastValue; i++) {
            ret += "[" + i + "] = " + calls[i] + " -> " + print(calls[i]) + "\n";
            total += calls[i];
        }
        ret += "Total calls " + total;
        return ret;
    }
}
