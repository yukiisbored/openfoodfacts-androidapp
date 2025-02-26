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

package openfoodfacts.github.scrachx.openfood.features.product.view.ingredients;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.afollestad.materialdialogs.MaterialDialog;
import com.squareup.picasso.Picasso;

import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.reactivex.disposables.CompositeDisposable;
import openfoodfacts.github.scrachx.openfood.AppFlavors;
import openfoodfacts.github.scrachx.openfood.R;
import openfoodfacts.github.scrachx.openfood.customtabs.CustomTabActivityHelper;
import openfoodfacts.github.scrachx.openfood.customtabs.CustomTabsHelper;
import openfoodfacts.github.scrachx.openfood.customtabs.WebViewFallback;
import openfoodfacts.github.scrachx.openfood.databinding.FragmentIngredientsProductBinding;
import openfoodfacts.github.scrachx.openfood.features.FullScreenActivityOpener;
import openfoodfacts.github.scrachx.openfood.features.ImagesManageActivity;
import openfoodfacts.github.scrachx.openfood.features.LoginActivity;
import openfoodfacts.github.scrachx.openfood.features.additives.AdditiveFragmentHelper;
import openfoodfacts.github.scrachx.openfood.features.product.edit.ProductEditActivity;
import openfoodfacts.github.scrachx.openfood.features.search.ProductSearchActivity;
import openfoodfacts.github.scrachx.openfood.features.shared.BaseFragment;
import openfoodfacts.github.scrachx.openfood.images.ProductImage;
import openfoodfacts.github.scrachx.openfood.models.Product;
import openfoodfacts.github.scrachx.openfood.models.ProductState;
import openfoodfacts.github.scrachx.openfood.models.entities.SendProduct;
import openfoodfacts.github.scrachx.openfood.models.entities.additive.AdditiveName;
import openfoodfacts.github.scrachx.openfood.models.entities.allergen.AllergenName;
import openfoodfacts.github.scrachx.openfood.models.entities.allergen.AllergenNameDao;
import openfoodfacts.github.scrachx.openfood.network.OpenFoodAPIClient;
import openfoodfacts.github.scrachx.openfood.network.WikiDataApiClient;
import openfoodfacts.github.scrachx.openfood.utils.BottomScreenCommon;
import openfoodfacts.github.scrachx.openfood.utils.FileUtils;
import openfoodfacts.github.scrachx.openfood.utils.FragmentUtils;
import openfoodfacts.github.scrachx.openfood.utils.LocaleHelper;
import openfoodfacts.github.scrachx.openfood.utils.PhotoReceiverHandler;
import openfoodfacts.github.scrachx.openfood.utils.ProductInfoState;
import openfoodfacts.github.scrachx.openfood.utils.SearchType;
import openfoodfacts.github.scrachx.openfood.utils.Utils;

import static android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE;
import static openfoodfacts.github.scrachx.openfood.models.ProductImageField.INGREDIENTS;
import static openfoodfacts.github.scrachx.openfood.utils.Utils.bold;
import static org.apache.commons.lang.StringUtils.isNotBlank;

public class IngredientsProductFragment extends BaseFragment implements IIngredientsProductPresenter.View {
    public static final Pattern INGREDIENT_PATTERN = Pattern.compile("[\\p{L}\\p{Nd}(),.-]+");
    private FragmentIngredientsProductBinding binding;
    private AllergenNameDao mAllergenNameDao;
    private OpenFoodAPIClient client;
    private String mUrlImage;
    private ProductState activityProductState;
    private String barcode;
    private SendProduct mSendProduct;
    private WikiDataApiClient wikidataClient;
    private CustomTabActivityHelper customTabActivityHelper;
    private CustomTabsIntent customTabsIntent;
    private IIngredientsProductPresenter.Actions presenter;
    private boolean extractIngredients = false;
    private boolean sendUpdatedIngredientsImage = false;
    private final CompositeDisposable disp = new CompositeDisposable();
    /**
     * boolean to determine if image should be loaded or not
     **/
    private boolean isLowBatteryMode = false;
    private PhotoReceiverHandler photoReceiverHandler;
    ActivityResultLauncher<Product> productActivityResultLauncher = registerForActivityResult(
        new ProductEditActivity.EditProductSendUpdatedImg(),
        (ActivityResultCallback<Boolean>) result -> {
            if (result) {
                onRefresh();
            }
        });
    ActivityResultLauncher<Void> loginActivityResultLauncher = registerForActivityResult(
        new LoginActivity.LoginContract(),
        (ActivityResultCallback<Boolean>) result -> {
            ProductEditActivity.start(getContext(),
                activityProductState,
                sendUpdatedIngredientsImage,
                extractIngredients);
        });


    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        customTabActivityHelper = new CustomTabActivityHelper();
        customTabsIntent = CustomTabsHelper.getCustomTabsIntent(getContext(), customTabActivityHelper.getSession());

        activityProductState = FragmentUtils.requireStateFromArguments(this);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        client = new OpenFoodAPIClient(requireContext());
        wikidataClient = new WikiDataApiClient();
        binding = FragmentIngredientsProductBinding.inflate(inflater);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        activityProductState = FragmentUtils.getStateFromArguments(this);
        binding.extractIngredientsPrompt.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_add_box_blue_18dp, 0, 0, 0);
        binding.changeIngImg.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_add_a_photo_blue_18dp, 0, 0, 0);

        binding.changeIngImg.setOnClickListener(v -> changeIngImage());
        binding.novaMethodLink.setOnClickListener(v -> novaMethodLinkDisplay());
        binding.extractIngredientsPrompt.setOnClickListener(v -> extractIngredients());
        binding.imageViewIngredients.setOnClickListener(v -> openFullScreen());

        photoReceiverHandler = new PhotoReceiverHandler(this::onPhotoReturned);
        refreshView(activityProductState);
    }

    @Override
    public void refreshView(ProductState productState) {
        super.refreshView(productState);
        activityProductState = productState;
        String langCode = LocaleHelper.getLanguage(getContext());
        if (getArguments() != null) {
            mSendProduct = (SendProduct) getArguments().getSerializable("sendProduct");
        }

        mAllergenNameDao = Utils.getDaoSession().getAllergenNameDao();

        // If Battery Level is low and the user has checked the Disable Image in Preferences , then set isLowBatteryMode to true
        if (Utils.isDisableImageLoad(requireContext()) && Utils.isBatteryLevelLow(requireContext())) {
            isLowBatteryMode = true;
        }

        final Product product = activityProductState.getProduct();
        presenter = new IngredientsProductPresenter(product, this);
        barcode = product.getCode();
        List<String> vitaminTagsList = product.getVitaminTags();
        List<String> aminoAcidTagsList = product.getAminoAcidTags();
        List<String> mineralTags = product.getMineralTags();
        List<String> otherNutritionTags = product.getOtherNutritionTags();

        if (!vitaminTagsList.isEmpty()) {
            binding.cvVitaminsTagsText.setVisibility(View.VISIBLE);
            binding.vitaminsTagsText.setText(bold(getString(R.string.vitamin_tags_text)));
            binding.vitaminsTagsText.append(buildStringBuilder(vitaminTagsList, Utils.SPACE));
        }

        if (!aminoAcidTagsList.isEmpty()) {
            binding.cvAminoAcidTagsText.setVisibility(View.VISIBLE);
            binding.aminoAcidTagsText.setText(bold(getString(R.string.amino_acid_tags_text)));
            binding.aminoAcidTagsText.append(buildStringBuilder(aminoAcidTagsList, Utils.SPACE));
        }

        if (!mineralTags.isEmpty()) {
            binding.cvMineralTagsText.setVisibility(View.VISIBLE);
            binding.mineralTagsText.setText(bold(getString(R.string.mineral_tags_text)));
            binding.mineralTagsText.append(buildStringBuilder(mineralTags, Utils.SPACE));
        }

        if (!otherNutritionTags.isEmpty()) {
            binding.otherNutritionTags.setVisibility(View.VISIBLE);
            binding.otherNutritionTags.setText(bold(getString(R.string.other_tags_text)));
            binding.otherNutritionTags.append(buildStringBuilder(otherNutritionTags, Utils.SPACE));
        }

        binding.textAdditiveProduct.setText(bold(getString(R.string.txtAdditives)));
        presenter.loadAdditives();

        if (isNotBlank(product.getImageIngredientsUrl(langCode))) {
            binding.ingredientImagetipBox.setTipMessage(getString(R.string.onboarding_hint_msg, getString(R.string.ingredient_image_edit_tip)));
            binding.ingredientImagetipBox.loadToolTip();
            binding.addPhotoLabel.setVisibility(View.GONE);
            binding.changeIngImg.setVisibility(View.VISIBLE);

            // Load Image if isLowBatteryMode is false
            if (!isLowBatteryMode) {
                Utils.picassoBuilder(getContext())
                    .load(product.getImageIngredientsUrl(langCode))
                    .into(binding.imageViewIngredients);
            } else {
                binding.imageViewIngredients.setVisibility(View.GONE);
            }
            mUrlImage = product.getImageIngredientsUrl(langCode);
        }

        //useful when this fragment is used in offline saving
        if (mSendProduct != null && isNotBlank(mSendProduct.getImgupload_ingredients())) {
            binding.addPhotoLabel.setVisibility(View.GONE);
            mUrlImage = mSendProduct.getImgupload_ingredients();
            Picasso.get().load(FileUtils.LOCALE_FILE_SCHEME + mUrlImage).config(Bitmap.Config.RGB_565).into(binding.imageViewIngredients);
        }

        List<String> allergens = getAllergens();

        if (activityProductState != null && StringUtils.isNotEmpty(product.getIngredientsText(langCode))) {
            binding.cvTextIngredientProduct.setVisibility(View.VISIBLE);
            SpannableStringBuilder txtIngredients = new SpannableStringBuilder(product.getIngredientsText(langCode).replace("_", ""));
            txtIngredients = setSpanBoldBetweenTokens(txtIngredients, allergens);
            if (TextUtils.isEmpty(product.getIngredientsText(langCode))) {
                binding.extractIngredientsPrompt.setVisibility(View.VISIBLE);
            }
            int ingredientsListAt = Math.max(0, txtIngredients.toString().indexOf(":"));
            if (!txtIngredients.toString().substring(ingredientsListAt).trim().isEmpty()) {
                binding.textIngredientProduct.setText(txtIngredients);
            }
        } else {
            binding.cvTextIngredientProduct.setVisibility(View.GONE);
            if (isNotBlank(product.getImageIngredientsUrl(langCode))) {
                binding.extractIngredientsPrompt.setVisibility(View.VISIBLE);
            }
        }
        presenter.loadAllergens();

        if (!StringUtils.isBlank(product.getTraces())) {
            String language = LocaleHelper.getLanguage(getContext());
            binding.cvTextTraceProduct.setVisibility(View.VISIBLE);
            binding.textTraceProduct.setMovementMethod(LinkMovementMethod.getInstance());
            binding.textTraceProduct.setText(bold(getString(R.string.txtTraces)));
            binding.textTraceProduct.append(" ");

            String[] traces = product.getTraces().split(",");
            for (int i = 0; i < traces.length; i++) {
                String trace = traces[i];
                if (i > 0) {
                    binding.textTraceProduct.append(", ");
                }
                binding.textTraceProduct.append(Utils.getClickableText(getTracesName(language, trace), trace, SearchType.TRACE, getActivity(), customTabsIntent));
            }
        } else {
            binding.cvTextTraceProduct.setVisibility(View.GONE);
        }

        if (product.getNovaGroups() != null) {
            binding.novaLayout.setVisibility(View.VISIBLE);
            binding.novaExplanation.setText(Utils.getNovaGroupExplanation(product.getNovaGroups(), requireContext()));
            binding.novaGroup.setImageResource(Utils.getNovaGroupDrawable(product));
            binding.novaGroup.setOnClickListener((View v) -> {
                Uri uri = Uri.parse(getString(R.string.url_nova_groups));
                CustomTabsIntent tabsIntent = CustomTabsHelper.getCustomTabsIntent(requireContext(), customTabActivityHelper.getSession());
                CustomTabActivityHelper.openCustomTab(IngredientsProductFragment.this.requireActivity(), tabsIntent, uri, new WebViewFallback());
            });
        } else {
            binding.novaLayout.setVisibility(View.GONE);
        }
    }

    private String getTracesName(String languageCode, String tag) {
        AllergenName allergenName = mAllergenNameDao.queryBuilder().where(AllergenNameDao.Properties.AllergenTag.eq(tag), AllergenNameDao.Properties.LanguageCode.eq(languageCode))
            .unique();
        if (allergenName != null) {
            return allergenName.getName();
        }
        return tag;
    }

    @NonNull
    private StringBuilder buildStringBuilder(@NonNull List<String> stringList, String prefix) {
        StringBuilder otherNutritionStringBuilder = new StringBuilder();
        for (String otherSubstance : stringList) {
            otherNutritionStringBuilder.append(prefix);
            prefix = ", ";
            otherNutritionStringBuilder.append(trimLanguagePartFromString(otherSubstance));
        }
        return otherNutritionStringBuilder;
    }

    @NonNull
    private CharSequence getAllergensTag(@NonNull AllergenName allergen) {
        SpannableStringBuilder ssb = new SpannableStringBuilder();

        ClickableSpan clickableSpan = new ClickableSpan() {
            @Override
            public void onClick(@NonNull View view) {
                if (allergen.getIsWikiDataIdPresent()) {
                    wikidataClient.doSomeThing(
                        allergen.getWikiDataId(),
                        result -> {
                            if (result != null) {
                                FragmentActivity activity = getActivity();
                                if (activity != null && !activity.isFinishing()) {
                                    BottomScreenCommon.showBottomSheet(result, allergen,
                                        activity.getSupportFragmentManager());
                                }
                            } else {
                                ProductSearchActivity.start(getContext(),
                                    allergen.getAllergenTag(),
                                    allergen.getName(),
                                    SearchType.ALLERGEN);
                            }
                        });
                } else {
                    ProductSearchActivity.start(getContext(),
                        allergen.getAllergenTag(),
                        allergen.getName(),
                        SearchType.ALLERGEN);
                }
            }
        };

        ssb.append(allergen.getName());
        ssb.setSpan(clickableSpan, 0, ssb.length(), SPAN_EXCLUSIVE_EXCLUSIVE);
        // If allergen is not in the taxonomy list then italicize it
        if (!allergen.isNotNull()) {
            StyleSpan iss =
                new StyleSpan(android.graphics.Typeface.ITALIC); //Span to make text italic
            ssb.setSpan(iss, 0, ssb.length(), SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return ssb;
    }

    /**
     * @return the string after trimming the language code from the tags
     *     like it returns folic-acid for en:folic-acid
     */
    private String trimLanguagePartFromString(String string) {
        return string.substring(3);
    }

    private SpannableStringBuilder setSpanBoldBetweenTokens(CharSequence text, List<String> allergens) {
        final SpannableStringBuilder ssb = new SpannableStringBuilder(text);
        Matcher m = INGREDIENT_PATTERN.matcher(ssb);
        while (m.find()) {
            final String tm = m.group();
            final String allergenValue = tm.replaceAll("[(),.-]+", "");

            for (String allergen : allergens) {
                if (allergen.equalsIgnoreCase(allergenValue)) {
                    int start = m.start();
                    int end = m.end();

                    if (tm.contains("(")) {
                        start += 1;
                    } else if (tm.contains(")")) {
                        end -= 1;
                    }

                    ssb.setSpan(new StyleSpan(Typeface.BOLD), start, end, SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
        }
        ssb.insert(0, Utils.bold(getString(R.string.txtIngredients) + ' '));
        return ssb;
    }

    @Override
    public void showAdditives(List<AdditiveName> additives) {
        AdditiveFragmentHelper.showAdditives(additives, binding.textAdditiveProduct, wikidataClient, this);
    }

    @Override
    public void showAdditivesState(ProductInfoState state) {
        switch (state) {
            case LOADING:
                binding.cvTextAdditiveProduct.setVisibility(View.VISIBLE);
                binding.textAdditiveProduct.append(getString(R.string.txtLoading));
                break;

            case EMPTY:
                binding.cvTextAdditiveProduct.setVisibility(View.GONE);
                break;
        }
    }

    @Override
    public void showAllergens(List<AllergenName> allergens) {
        binding.textSubstanceProduct.setMovementMethod(LinkMovementMethod.getInstance());
        binding.textSubstanceProduct.setText(bold(getString(R.string.txtSubstances)));
        binding.textSubstanceProduct.append(" ");

        for (int i = 0, lastIdx = allergens.size() - 1; i <= lastIdx; i++) {
            AllergenName allergen = allergens.get(i);
            binding.textSubstanceProduct.append(getAllergensTag(allergen));
            // Add comma if not the last item
            if (i != lastIdx) {
                binding.textSubstanceProduct.append(", ");
            }
        }
    }

    @Override
    public void onDestroy() {
        disp.dispose();
        binding = null;
        super.onDestroy();
    }

    public void changeIngImage() {
        sendUpdatedIngredientsImage = true;

        if (getActivity() == null) {
            return;
        }
        final ViewPager2 viewPager = getActivity().findViewById(R.id.pager);
        if (AppFlavors.isFlavors(AppFlavors.OFF)) {
            final SharedPreferences settings = getActivity().getSharedPreferences("login", 0);
            final String login = settings.getString("user", "");
            if (TextUtils.isEmpty(login)) {
                showSignInDialog();
            } else {
                activityProductState = FragmentUtils.getStateFromArguments(this);
                if (activityProductState != null) {
                    productActivityResultLauncher.launch(activityProductState.getProduct());
                }
            }
        }
        if (AppFlavors.isFlavors(AppFlavors.OPFF)) {
            viewPager.setCurrentItem(4);
        }

        if (AppFlavors.isFlavors(AppFlavors.OBF)) {
            viewPager.setCurrentItem(1);
        }

        if (AppFlavors.isFlavors(AppFlavors.OPF)) {
            viewPager.setCurrentItem(0);
        }
    }

    @Override
    public void showAllergensState(ProductInfoState state) {
        switch (state) {
            case LOADING:
                binding.textSubstanceProduct.setVisibility(View.VISIBLE);
                binding.textSubstanceProduct.append(getString(R.string.txtLoading));
                break;

            case EMPTY:
                binding.textSubstanceProduct.setVisibility(View.GONE);
                break;
        }
    }

    private List<String> getAllergens() {
        List<String> allergens = activityProductState.getProduct().getAllergensTags();
        if (activityProductState.getProduct() == null || allergens == null || allergens.isEmpty()) {
            return Collections.emptyList();
        } else {
            return allergens;
        }
    }

    void novaMethodLinkDisplay() {
        if (activityProductState != null && activityProductState.getProduct() != null && activityProductState.getProduct().getNovaGroups() != null) {
            Uri uri = Uri.parse(getString(R.string.url_nova_groups));
            CustomTabsIntent tabsIntent = CustomTabsHelper.getCustomTabsIntent(requireContext(), customTabActivityHelper.getSession());
            CustomTabActivityHelper.openCustomTab(requireActivity(), tabsIntent, uri, new WebViewFallback());
        }
    }

    public void extractIngredients() {
        extractIngredients = true;
        final SharedPreferences settings = requireActivity().getSharedPreferences("login", 0);
        final String login = settings.getString("user", "");
        if (login.isEmpty()) {
            showSignInDialog();
        } else {
            activityProductState = FragmentUtils.requireStateFromArguments(this);
            registerForActivityResult(new ProductEditActivity.EditProductPerformOCR(), result -> {
                if (result) {
                    onRefresh();
                }
            }).launch(activityProductState.getProduct());
        }
    }

    private void showSignInDialog() {
        new MaterialDialog.Builder(requireContext())
            .title(R.string.sign_in_to_edit)
            .positiveText(R.string.txtSignIn)
            .negativeText(R.string.dialog_cancel)
            .onPositive((dialog, which) -> {
                loginActivityResultLauncher.launch(null);
                dialog.dismiss();
            })
            .onNegative((dialog, which) -> dialog.dismiss())
            .build().show();
    }

    private void openFullScreen() {
        if (mUrlImage != null && activityProductState != null && activityProductState.getProduct() != null) {
            FullScreenActivityOpener.openForUrl(this, activityProductState.getProduct(), INGREDIENTS, mUrlImage, binding.imageViewIngredients);
        } else {
            newIngredientImage();
        }
    }

    public void newIngredientImage() {
        doChooseOrTakePhotos(getString(R.string.ingredients_picture));
    }

    @Override
    protected void doOnPhotosPermissionGranted() {
        newIngredientImage();
    }

    public void onPhotoReturned(File newPhotoFile) {
        ProductImage image = new ProductImage(barcode, INGREDIENTS, newPhotoFile);
        image.setFilePath(newPhotoFile.getAbsolutePath());
        disp.add(client.postImg(image).subscribe());
        binding.addPhotoLabel.setVisibility(View.GONE);
        mUrlImage = newPhotoFile.getAbsolutePath();

        Picasso.get()
            .load(newPhotoFile)
            .fit()
            .into(binding.imageViewIngredients);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (ImagesManageActivity.isImageModified(requestCode, resultCode)) {
            onRefresh();
        }

        photoReceiverHandler.onActivityResult(this, requestCode, resultCode, data);
    }

    public String getIngredients() {
        return mUrlImage;
    }

    @Override
    public void onDestroyView() {
        if (presenter != null) {
            presenter.dispose();
        }
        super.onDestroyView();
    }
}
