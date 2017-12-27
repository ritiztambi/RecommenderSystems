package org.lenskit.mooc.ii;

import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.longs.Long2DoubleMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import org.apache.commons.lang3.tuple.Pair;
import org.lenskit.data.dao.DataAccessObject;
import org.lenskit.data.entities.CommonAttributes;
import org.lenskit.data.ratings.Rating;
import org.lenskit.data.ratings.Ratings;
import org.lenskit.inject.Transient;
import org.lenskit.util.IdBox;
import org.lenskit.util.collections.LongUtils;
import org.lenskit.util.io.ObjectStream;
import org.lenskit.util.math.Vectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.List;
import java.util.Map;

/**
g * @author <a href="http://www.grouplens.org">GroupLens Research</a>
 */
public class SimpleItemItemModelProvider implements Provider<SimpleItemItemModel> {
    private static final Logger logger = LoggerFactory.getLogger(SimpleItemItemModelProvider.class);

    private final DataAccessObject dao;

    /**
     * Construct the model provider.
     * @param dao The data access object.
     */
    @Inject
    public SimpleItemItemModelProvider(@Transient DataAccessObject dao) {
        this.dao = dao;
    }

    /**
     * Construct the item-item model.
     * @return The item-item model.
     */
    @Override
    public SimpleItemItemModel get() {
        Map<Long,Long2DoubleMap> itemVectors = Maps.newHashMap();
        Long2DoubleMap itemMeans = new Long2DoubleOpenHashMap();

        try (ObjectStream<IdBox<List<Rating>>> stream = dao.query(Rating.class)
                                                           .groupBy(CommonAttributes.ITEM_ID)
                                                           .stream()) {
            for (IdBox<List<Rating>> item : stream) {
                long itemId = item.getId();
                List<Rating> itemRatings = item.getValue();
                Long2DoubleOpenHashMap ratings = new Long2DoubleOpenHashMap(Ratings.itemRatingVector(itemRatings));

                // Compute and store the item's mean.
                double mean = Vectors.mean(ratings);
                itemMeans.put(itemId, mean);

                // Mean center the ratings.
                for (Map.Entry<Long, Double> entry : ratings.entrySet()) {
                    entry.setValue(entry.getValue() - mean);
                }

                itemVectors.put(itemId, LongUtils.frozenMap(ratings));
            }
        }

        // Map items to vectors (maps) of item similarities.
        Map<Long,Long2DoubleMap> itemSimilarities = Maps.newHashMap();

        for(Map.Entry<Long,Long2DoubleMap> e1: itemVectors.entrySet()) {
            Long item1_id =e1.getKey();
            Long2DoubleMap item1_rating_vector=e1.getValue();
            Long2DoubleMap item1_similarity = new Long2DoubleOpenHashMap();
            for(Map.Entry<Long, Long2DoubleMap> e2: itemVectors.entrySet()) {
                Long item2_id = e2.getKey();
                Long2DoubleMap item2_rating_vector = e2.getValue();
                Double target_user=0.0, other_user=0.0;
                for (Map.Entry<Long, Double> entry1 : item1_rating_vector.entrySet()) {
                    Double val1 = entry1.getValue();
                    target_user = target_user + (val1 * val1);
                }
                for (Map.Entry<Long, Double> entry2 : item2_rating_vector.entrySet()) {
                    Double val2 = entry2.getValue();
                    other_user = other_user + (val2 * val2);
                }


                Double d = (Math.sqrt(target_user) * Math.sqrt(other_user));
                Double sim_val = new Double((Vectors.dotProduct(item1_rating_vector, item2_rating_vector))/d);

                if (sim_val > 0) { //positive similarities only
                    item1_similarity.put(item2_id, sim_val);
                }
            }
            itemSimilarities.put(item1_id,item1_similarity);
        }


        return new SimpleItemItemModel(LongUtils.frozenMap(itemMeans), itemSimilarities);
    }
}
