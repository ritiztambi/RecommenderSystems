package org.lenskit.mooc.hybrid;

import it.unimi.dsi.fastutil.longs.LongSet;
import org.lenskit.api.ItemScorer;
import org.lenskit.api.Result;
import org.lenskit.api.ResultMap;
import org.lenskit.basic.AbstractItemScorer;
import org.lenskit.bias.BiasModel;
import org.lenskit.bias.UserBiasModel;
import org.lenskit.data.ratings.RatingSummary;
import org.lenskit.results.Results;
import org.lenskit.util.collections.LongUtils;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Item scorer that does a logistic blend of a subsidiary item scorer and popularity.  It tries to predict
 * whether a user has rated a particular item.
 */
public class LogisticItemScorer extends AbstractItemScorer {
    private final LogisticModel logisticModel;
    private final BiasModel biasModel;
    private final RecommenderList recommenders;
    private final RatingSummary ratingSummary;

    @Inject
    public LogisticItemScorer(LogisticModel model, UserBiasModel bias, RecommenderList recs, RatingSummary rs) {
        logisticModel = model;
        biasModel = bias;
        recommenders = recs;
        ratingSummary = rs;
    }

    @Nonnull
    @Override
    public ResultMap scoreWithDetails(long user, @Nonnull Collection<Long> items) {
        // TODO Implement item scorer
        //throw new UnsupportedOperationException("item scorer not implemented");

        List<Result> res = new ArrayList<>();
        int count = 1+ recommenders.getRecommenderCount()+1;
        List<ItemScorer> s_list=recommenders.getItemScorers();
        for(long i: items)
        {
            double[] xvals = new double[count];
            double y=1;
            double bias_value= biasModel.getIntercept()+biasModel.getUserBias(user)+biasModel.getItemBias(i);

            xvals[0]=bias_value;
            xvals[1]=Math.log(ratingSummary.getItemRatingCount(i));


            int j=2;
            for(ItemScorer item_scorer: s_list) {


                Result temp=item_scorer.score(user,i);
                if(temp==null)
                    xvals[j++]=0;
                else
                    xvals[j++]=temp.getScore()-xvals[0];
            }


            double modified_Score=logisticModel.evaluate(y,xvals);

            res.add(Results.create(i, modified_Score));
        }
            return Results.newResultMap(res);
        }
    }

