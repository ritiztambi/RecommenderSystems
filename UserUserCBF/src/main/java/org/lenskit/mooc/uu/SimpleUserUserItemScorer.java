package org.lenskit.mooc.uu;

import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.longs.*;
import org.lenskit.api.Result;
import org.lenskit.api.ResultMap;
import org.lenskit.basic.AbstractItemScorer;
import org.lenskit.data.dao.DataAccessObject;
import org.lenskit.data.entities.CommonAttributes;
import org.lenskit.data.entities.CommonTypes;
import org.lenskit.data.ratings.Rating;
import org.lenskit.results.Results;
import org.lenskit.util.ScoredIdAccumulator;
import org.lenskit.util.TopNScoredIdAccumulator;
import org.lenskit.util.collections.LongUtils;
import org.lenskit.util.math.Scalars;
import org.lenskit.util.math.Vectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.*;

/**
 * User-user item scorer.
 * @author <a href="http://www.grouplens.org">GroupLens Research</a>
 */
public class SimpleUserUserItemScorer extends AbstractItemScorer {

    private static final Logger logger = LoggerFactory.getLogger(SimpleUserUserItemScorer.class);
    private final DataAccessObject dao;
    private final int neighborhoodSize;

    /**
     * Instantiate a new user-user item scorer.
     * @param dao The data access object.
     */
    @Inject
    public SimpleUserUserItemScorer(DataAccessObject dao) {
        this.dao = dao;
        neighborhoodSize = 30;
    }

    @Nonnull
    @Override
    public ResultMap scoreWithDetails(long user, @Nonnull Collection<Long> items) {

        // TODO Score the items for the user with user-user CF

        //Result List
        List<Result> results = new ArrayList<>();

        //User - RatingsList Map
        Map<Long,Long2DoubleOpenHashMap> userSet=new HashMap<>();
        LongSet users=dao.getEntityIds(CommonTypes.USER);

        // Average of the current User
        double average=0;

        //Iterate over users to calculate Normalized Ratings
        for(Long User:users){

            Long2DoubleOpenHashMap ratings=new Long2DoubleOpenHashMap();
            ratings=getUserRatingVector(User);

            //Calculating average
            Iterator it=ratings.keySet().iterator();
            double avg=0;

            while(it.hasNext()){
                avg=avg+ratings.get((long)it.next());

            }
            avg=avg/ratings.size();

            //Store Average of Target User
            if(User==user){
                average=avg;
            }

            //Adjusting ratings and storing them in the map
            it=ratings.keySet().iterator();
            while(it.hasNext()){
                long key= (long) it.next();
                ratings.put(key,ratings.get(key)-avg);
            }
            userSet.put(User,ratings);

        }


        // Creating Valid Movie - User List;
        Map<Long,LongArraySet> itemUser = new HashMap<>();
        for(long item : items){
            List<Rating> history=dao.query(Rating.class)
                    .withAttribute(CommonAttributes.ITEM_ID,item)
                    .get();
            LongArraySet u=new LongArraySet();
            for(Rating r: history){
                if(user!=r.getUserId()){                // User's own rating not accounted for in the list;
                    u.add((r.getUserId()));
                }

            }
            itemUser.put(item,u);

        }



        //Iterate over each item
        for(long item : items){

            //Stores User and Similarity value with target User
            class Similarity{
                long user;
                double value;
                    Similarity(long user, double value){
                    this.user=user;
                    this.value=value;
                }

                @Override
                public String toString() {
                    return new String(user+" "+value);
                }
            }

            //Target User's Similarity List
            List<Similarity> similarity=new ArrayList<Similarity>();
            LongArraySet Users=new LongArraySet();
            Users=itemUser.get(item);

            if(Users.size()<2) continue;        //Ignore if neighbourhood size less than 2

            //Iterate over Users to calculate similarity
            for(long User: Users){
               Long2DoubleOpenHashMap ratings1=new Long2DoubleOpenHashMap();
               Long2DoubleOpenHashMap ratings2=new Long2DoubleOpenHashMap();
               ratings1=userSet.get(User);
               ratings2=userSet.get(user);

               double num=0;

               Iterator it=ratings2.keySet().iterator();
               while(it.hasNext()){
                   long key=(long)it.next();
                   if(ratings1.containsKey(key)){
                       double p=ratings1.get(key);
                       double q=ratings2.get(key);
                       num+=p*q;
                   }
               }
               it=ratings2.keySet().iterator();
               double psq=0;
               while(it.hasNext()) {
                   long key = (long) it.next();
                   double p = ratings2.get(key);
                   psq += p * p;
               }
               it=ratings1.keySet().iterator();
               double qsq=0;
               while(it.hasNext()){
                    long key=(long)it.next();
                    double q=ratings1.get(key);
                    qsq+=q*q;
               }

               double denom=Math.sqrt(psq)*Math.sqrt(qsq);
               double cosine;
               if(denom!=0) {
                    cosine = num / denom;
                    if(cosine>0){
                        similarity.add(new Similarity(User,cosine));
                    }
               }
            }

            //Sort the Similarity Matrix
            Comparator<Similarity> comp=new Comparator<Similarity>() {
                @Override
                public int compare(Similarity o1,Similarity o2) {
                    if(o1.value>o2.value){
                        return -1;
                    }
                    else if(o1.value==o2.value){
                        return 0;
                    }
                    else
                        return 1;
                }
            };
            if(similarity.size()<2){
                continue;
            }
            similarity.sort(comp);



            //Restrict Neighbourhood to neighbourhoodSize
            int count=0;
            List<Similarity>  similarities = new ArrayList<>();
            Iterator it=similarity.iterator();
            while(it.hasNext()){
                count++;
                if(count>neighborhoodSize){
                    break;
                }
                Similarity sim= (Similarity) it.next();
                similarities.add(sim);
            }

            //Compute Prediction for selected neighbourhood
            double pred=average;
            double num=0;
            for(Similarity sim : similarities){

                num+=sim.value*userSet.get(sim.user).get(item);
            }
            double denom=0;
            for(Similarity sim : similarities){
                denom+=sim.value;
            }
            pred+=num/denom;


            // Store predictions in Result list
            Result res=Results.create(item,pred);
            results.add(res);
        }


         return Results.newResultMap(results);

    }

    /**
     * Get a user's rating vector.
     * @param user The user ID.
     * @return The rating vector, mapping item IDs to the user's rating
     *         for that item.
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
