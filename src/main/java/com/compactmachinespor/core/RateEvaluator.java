package com.compactmachinespor.core;

import java.util.Random;

public class RateEvaluator {

    /**
     * Refactored using RANSAC to find the most consistent throughput rate.
     * @param timeSeriesData IO data array aggregated by second.
     * @return Fitted stable tick yield (k-value).
     */
    public static double evaluateStableRate(int[] timeSeriesData) {
        int n = timeSeriesData.length;
        if (n < 2) return 0;

        // 1. Calculate cumulative sum (The "integral" of yield)
        long[] C = new long[n];
        C[0] = timeSeriesData[0];
        for (int i = 1; i < n; i++) {
            C[i] = C[i - 1] + timeSeriesData[i];
        }

        // RANSAC Parameters
        int iterations = 100;    // Number of random trials
        double threshold = 2.0;  // Max vertical distance to be an "inlier"
        int bestInlierCount = -1;
        double bestSlope = 0;

        Random rand = new Random();

        // 2. RANSAC Loop
        for (int i = 0; i < iterations; i++) {
            // Pick two random distinct indices
            int idx1 = rand.nextInt(n);
            int idx2 = rand.nextInt(n);
            if (idx1 == idx2) continue;

            // Calculate slope (k) and intercept (b) between these two points
            // Formula: k = (y2 - y1) / (x2 - x1)
            double k = (double) (C[idx2] - C[idx1]) / (idx2 - idx1);
            double b = C[idx1] - k * idx1;

            int currentInliers = 0;
            // 3. Count how many points fit this linear model
            for (int t = 0; t < n; t++) {
                double expectedC = k * t + b;
                if (Math.abs(C[t] - expectedC) < threshold) {
                    currentInliers++;
                }
            }

            // 4. Keep the model that explains the most data
            if (currentInliers > bestInlierCount) {
                bestInlierCount = currentInliers;
                bestSlope = k;
            }
        }

        // 5. Final conversion
        // Ensure we don't return negative rates from weird noise
        double finalRatePerSecond = Math.max(0, bestSlope);
        
        // Convert to yield per tick (Minecraft 20 TPS standard)
        return finalRatePerSecond / 20.0;
    }
}
