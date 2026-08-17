package com.compactmachinespor.core;

public class RateEvaluator {
    /**
     * @param timeSeriesData IO data array aggregated by second (e.g., length = 300)
     * @return Fitted stable tick yield (k-value)
     */
    public static double evaluateStableRate(long[] timeSeriesData) {
        int n = timeSeriesData.length;
        if (n == 0) return 0;

        // 1. Calculate cumulative sum
        // C[t] represents the total IO volume for the first t seconds
        long[] C = new long[n + 1];
        for (int i = 0; i < n; i++) {
            C[i + 1] = C[i] + timeSeriesData[i];
        }

        // 2. Set reference region (assume the machine is stable in the last 1/3 of the time)
        int tRef = (int) ((long) n * 2 / 3); // Prevent int overflow
        if (n - tRef <= 0) return 0; // Prevent boundary cases where the array is too small

        // Calculate baseline per-second yield (slope) for the reference interval
        double kRef = (double) (C[n] - C[tRef]) / (n - tRef);

        // 3. Calculate maximum oscillation amplitude within the reference region
        double maxDev = 0;
        for (int t = tRef; t <= n; t++) {
            // E_t is the theoretical cumulative amount deduced backward from the end
            double expectedC = C[n] - kRef * (n - t);
            double deviation = C[t] - expectedC;
            if (Math.abs(deviation) > maxDev) {
                maxDev = Math.abs(deviation);
            }
        }

        // 4. Set tolerance threshold
        // Tolerance = max(2 * oscillation amplitude, 1.5 * yield, minimum protection)
        double tolerance = Math.max(maxDev * 2.0, Math.max(kRef * 1.5, 1.0));

        // 5. Search backward for the boundary of warm-up/cache dumping period
        int stableStart = 0;
        for (int t = tRef - 1; t >= 0; t--) {
            double expectedC = C[n] - kRef * (n - t);
            double deviation = Math.abs(C[t] - expectedC);

            // If deviation exceeds tolerance, t is in an unstable state (warm-up or dumping)
            if (deviation > tolerance) {
                stableStart = t + 1; // Stable period starts from the next moment
                break;
            }
        }

        // 6. Calculate final result
        int stableDuration = n - stableStart;
        if (stableDuration <= 0) return 0;

        double finalRatePerSecond = (double) (C[n] - C[stableStart]) / stableDuration;
        // Convert to yield per tick (divide by 20)
        return finalRatePerSecond / 20.0;
    }

    public static double evaluateStableRate(int[] timeSeriesData) {
        long[] converted = new long[timeSeriesData.length];
        for (int i = 0; i < timeSeriesData.length; i++) {
            converted[i] = timeSeriesData[i];
        }
        return evaluateStableRate(converted);
    }
}
