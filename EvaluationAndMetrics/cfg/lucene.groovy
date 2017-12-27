import org.grouplens.lenskit.vectors.similarity.CosineVectorSimilarity
import org.grouplens.lenskit.vectors.similarity.PearsonCorrelation
import org.grouplens.lenskit.vectors.similarity.VectorSimilarity
import org.lenskit.api.ItemScorer
import org.lenskit.bias.BiasModel
import org.lenskit.bias.ItemBiasModel
import org.lenskit.bias.UserBiasModel
import org.lenskit.knn.NeighborhoodSize
import org.lenskit.knn.item.ItemItemScorer
import org.lenskit.knn.item.model.ItemItemModel
import org.lenskit.knn.user.UserUserItemScorer
import org.lenskit.transform.normalize.BiasUserVectorNormalizer
import org.lenskit.transform.normalize.MeanCenteringVectorNormalizer
import org.lenskit.transform.normalize.UserVectorNormalizer
import org.lenskit.transform.normalize.VectorNormalizer
import org.lenskit.mooc.cbf.LuceneItemItemModel


for (nnbrs in [5, 10, 15, 20, 25, 30, 40, 50, 75, 100]) {
    algorithm("Lucene") {
        attributes["NNbrs"] = nnbrs
// use fallback scorer for unscorable items
        include'fallback.groovy'
        bind ItemScorer to ItemItemScorer
        bind ItemItemModel to LuceneItemItemModel
        set NeighborhoodSize to nnbrs
    }

    algorithm("LuceneNorm") {
        attributes["NNbrs"] = nnbrs
        include 'fallback.groovy'
        bind ItemScorer to ItemItemScorer
        bind ItemItemModel to LuceneItemItemModel
        set NeighborhoodSize to nnbrs
// normalize user rating vectors by subtracting biases
        bind UserVectorNormalizer to BiasUserVectorNormalizer
// subtract the item bias (so we normalize by item mean rating)
        within(UserVectorNormalizer) {
            bind BiasModel to ItemBiasModel

        }
    }
}