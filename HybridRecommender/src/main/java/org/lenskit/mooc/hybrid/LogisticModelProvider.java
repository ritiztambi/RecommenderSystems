package org.lenskit.mooc.hybrid;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;
import org.lenskit.api.ItemScorer;
import org.lenskit.api.Result;
import org.lenskit.bias.BiasModel;
import org.lenskit.bias.UserBiasModel;
import org.lenskit.data.ratings.Rating;
import org.lenskit.data.ratings.RatingSummary;
import org.lenskit.inject.Transient;
import org.lenskit.util.ProgressLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.*;

/**
 * Trainer that builds logistic models.
 */
public class LogisticModelProvider implements Provider<LogisticModel> {
    private static final Logger logger = LoggerFactory.getLogger(LogisticModelProvider.class);
    private static final double LEARNING_RATE = 0.00005;
    private static final int ITERATION_COUNT = 100;

    private final LogisticTrainingSplit dataSplit;
    private final BiasModel baseline;
    private final RecommenderList recommenders;
    private final RatingSummary ratingSummary;
    private final int parameterCount;
    private final Random random;

    @Inject
    public LogisticModelProvider(@Transient LogisticTrainingSplit split,
                                 @Transient UserBiasModel bias,
                                 @Transient RecommenderList recs,
                                 @Transient RatingSummary rs,
                                 @Transient Random rng) {
        dataSplit = split;
        baseline = bias;
        recommenders = recs;
        ratingSummary = rs;
        parameterCount = 1 + recommenders.getRecommenderCount() + 1;
        random = rng;
    }

    @Override
    public LogisticModel get() {
        List<ItemScorer> scorers = recommenders.getItemScorers();
        double intercept = 0;
        double[] params = new double[parameterCount];

        LogisticModel current = LogisticModel.create(intercept, params);

        // TODO Implement model training
        Map<Rating, RealVector> vars = new HashMap<>();
        List<Rating> r = dataSplit.getTuneRatings();
        for (Rating ratings : r) {
            RealVector scores = new ArrayRealVector(params.length);
            double b = baseline.getIntercept() + baseline.getItemBias(ratings.getItemId()) + baseline.getUserBias(ratings.getUserId());
            scores.setEntry(0, b);
            scores.setEntry(1, Math.log(ratingSummary.getItemRatingCount(ratings.getItemId())));
            int c = 2;
            for (ItemScorer iterator : recommenders.getItemScorers()) {
                Result s = iterator.score(ratings.getUserId(), ratings.getItemId());
                double res;
                if (s == null)
                    res = 0;
                else
                    res = s.getScore() - b;
                scores.setEntry(c, res);
                c++;
            }
            vars.put(ratings, scores);

        }
        int i;
        for (i = 0; i < ITERATION_COUNT; i++) {
            Collections.shuffle(r);
            current = LogisticModel.create(intercept, params);
            for (Rating ratings : r) {
                RealVector v = vars.get(ratings);

                if (v == null) {
                    v = new ArrayRealVector(params.length);

                    double b = baseline.getIntercept() + baseline.getItemBias(ratings.getItemId()) + baseline.getUserBias(ratings.getUserId());
                    v.setEntry(0, b);
                    v.setEntry(1, Math.log(ratingSummary.getItemRatingCount(ratings.getItemId())));
                    int c = 2;
                    for (ItemScorer iterator : recommenders.getItemScorers()) {
                        Result s = iterator.score(ratings.getUserId(), ratings.getItemId());
                        double res;
                        if (s == null)
                            res = 0;
                        else
                            res = s.getScore() - b;
                        v.setEntry(c, res);
                        c++;
                    }

                }

                double updated_value = current.evaluate(-ratings.getValue(), v);

                intercept = intercept + (LEARNING_RATE * ratings.getValue() * updated_value);

                int j;
                for (j = 0; j < parameterCount; j++)
                    params[j] = params[j] + (LEARNING_RATE * (v.getEntry(j) * (ratings.getValue() * updated_value)));
                current = LogisticModel.create(intercept, params);
            }

        }

        return current;
    }
}
