package org.lenskit.mooc.ii;

import it.unimi.dsi.fastutil.longs.Long2DoubleMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import org.lenskit.api.Result;
import org.lenskit.api.ResultMap;
import org.lenskit.basic.AbstractItemScorer;
import org.lenskit.data.dao.DataAccessObject;
import org.lenskit.data.entities.CommonAttributes;
import org.lenskit.data.ratings.Rating;
import org.lenskit.results.Results;
import org.lenskit.util.ScoredIdAccumulator;
import org.lenskit.util.TopNScoredIdAccumulator;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.*;

/**
 * @author <a href="http://www.grouplens.org">GroupLens Research</a>
 */
public class SimpleItemItemScorer extends AbstractItemScorer {
    private final SimpleItemItemModel model;
    private final DataAccessObject dao;
    private final int neighborhoodSize;

    @Inject
    public SimpleItemItemScorer(SimpleItemItemModel m, DataAccessObject dao) {
        model = m;
        this.dao = dao;
        neighborhoodSize = 20;
    }

    /**
     * Score items for a user.
     * @param user The user ID.
     * @param items The score vector.  Its key domain is the items to score, and the scores
     *               (rating predictions) should be written back to this vector.
     */
    @Override
    public ResultMap scoreWithDetails(long user, @Nonnull Collection<Long> items) {
        Long2DoubleMap itemMeans = model.getItemMeans();
        Long2DoubleMap ratings = getUserRatingVector(user);

        for(Map.Entry<Long,Double> e1: ratings.entrySet()) {
            Long item_id=e1.getKey();
            e1.setValue((e1.getValue())-(itemMeans.get(item_id))); // Normalizing the user's ratings by subtracting the item mean from each one.
        }

        List<Result> results = new ArrayList<>();

        for (long item: items ) {

            Long2DoubleMap neighbors=model.getNeighbors(item);
            List<Result> r = new ArrayList<>();
            for(Map.Entry<Long,Double> entry: neighbors.entrySet()) {
                r.add(Results.create(entry.getKey(),entry.getValue()));
            }
            Collections.sort(r, new Comparator<Result>() { //sorting neighbors
                @Override
                public int compare(Result o1, Result o2) {
                    if(o1.getScore()>o2.getScore())
                        return -1;
                    else if(o1.getScore()<o2.getScore())
                        return 1;
                    else
                        return 0;

                }
            });
            int number_of_neighbors=0,c=0;
            Double n=0.0,d=0.0;
            while(number_of_neighbors<neighborhoodSize && number_of_neighbors<(neighbors.size())) {
                Result r1=r.get(c);
                Long item_id=r1.getId();
                Double sim_val=r1.getScore();
                if(ratings.containsKey(item_id) && item != item_id) {
                    n=n+(ratings.get(item_id)*sim_val);
                    d=d+sim_val;                            //evaluating fractional part of score
                    number_of_neighbors++;
                }
                c++;
                if(c==neighbors.size())
                    break;
            }

            Double prediction=itemMeans.get(item) +(n/d);
            results.add(Results.create(item,prediction));
        }

        return Results.newResultMap(results);

    }

    /**
     * Get a user's ratings.
     * @param user The user ID.
     * @return The ratings to retrieve.
     */
    private Long2DoubleOpenHashMap getUserRatingVector(long user) {
        List<Rating> history = dao.query(Rating.class)
                                  .withAttribute(CommonAttributes.USER_ID, user)
                                  .get();

        Long2DoubleOpenHashMap ratings = new Long2DoubleOpenHashMap();
        for (Rating r: history) {
            ratings.put(r.getItemId(), r.getValue());
        }

        return ratings;
    }


}
