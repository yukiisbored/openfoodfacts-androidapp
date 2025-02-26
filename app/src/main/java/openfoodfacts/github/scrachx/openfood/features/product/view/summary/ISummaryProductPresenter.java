
/*
 * Copyright 2016-2020 Open Food Facts
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package openfoodfacts.github.scrachx.openfood.features.product.view.summary;

import androidx.annotation.NonNull;

import java.util.List;

import openfoodfacts.github.scrachx.openfood.models.AnnotationAnswer;
import openfoodfacts.github.scrachx.openfood.models.AnnotationResponse;
import openfoodfacts.github.scrachx.openfood.models.Question;
import openfoodfacts.github.scrachx.openfood.models.entities.additive.AdditiveName;
import openfoodfacts.github.scrachx.openfood.models.entities.allergen.AllergenName;
import openfoodfacts.github.scrachx.openfood.models.entities.analysistagconfig.AnalysisTagConfig;
import openfoodfacts.github.scrachx.openfood.models.entities.category.CategoryName;
import openfoodfacts.github.scrachx.openfood.models.entities.label.LabelName;
import openfoodfacts.github.scrachx.openfood.utils.ProductInfoState;

/**
 * Created by Lobster on 17.03.18.
 */
public interface ISummaryProductPresenter {
    interface Actions {
        void loadProductQuestion();

        void annotateInsight(String insightId, AnnotationAnswer annotation);

        void loadAllergens(Runnable runIfError);

        void loadCategories();

        void loadLabels();

        void dispose();

        void loadAdditives();

        void loadAnalysisTags();
    }

    interface View {
        void showAllergens(@NonNull List<AllergenName> allergens);

        void showProductQuestion(Question question);

        void showAnnotatedInsightToast(AnnotationResponse annotationResponse);

        void showCategories(List<CategoryName> categories);

        void showLabels(@NonNull List<LabelName> labels);

        void showCategoriesState(ProductInfoState state);

        void showLabelsState(ProductInfoState state);

        void showAdditives(List<AdditiveName> additives);

        void showAdditivesState(ProductInfoState state);

        void showAnalysisTags(List<AnalysisTagConfig> analysisTags);
    }
}
