/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.plugin.globalactions.wallet;

import static com.android.systemui.plugin.globalactions.wallet.WalletCardCarousel.CARD_ANIM_ALPHA_DELAY;
import static com.android.systemui.plugin.globalactions.wallet.WalletCardCarousel.CARD_ANIM_ALPHA_DURATION;

import android.annotation.Nullable;
import android.content.Context;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.ContextThemeWrapper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListPopupWindow;
import android.widget.TextView;

import com.android.internal.annotations.VisibleForTesting;

import java.util.List;


/**
 * Contains the entire wallet view. Coordinates the label text with the currently selected card in
 * the contained carousel. Also capable of showing the lock screen error message.
 */
class WalletView extends FrameLayout implements WalletCardCarousel.OnCardScrollListener {

    private static final int CAROUSEL_IN_ANIMATION_DURATION = 300;
    private static final int CAROUSEL_OUT_ANIMATION_DURATION = 200;
    private static final int CARD_LABEL_ANIM_DELAY = 133;

    private final ViewGroup mCardCarouselContainer;
    private final WalletCardCarousel mCardCarousel;
    private final TextView mCardLabel;
    private final TextView mErrorView;
    private final ViewGroup mEmptyStateView;
    private final ImageView mOverflowButton;
    private final ArrayAdapter<OverflowItem> mOverflowAdapter;
    private final ListPopupWindow mOverflowPopup;
    private final int mIconSizePx;
    private final Interpolator mInInterpolator;
    private final Interpolator mOutInterpolator;
    private final float mAnimationTranslationX;
    private CharSequence mCenterCardText;

    public WalletView(Context context) {
        this(context, null);
    }

    public WalletView(Context context, AttributeSet attrs) {
        super(context, attrs);
        inflate(context, R.layout.wallet_view, this);
        mCardCarouselContainer = requireViewById(R.id.card_carousel_container);
        mCardCarousel = requireViewById(R.id.card_carousel);
        mCardCarousel.setCardScrollListener(this);
        mCardLabel = requireViewById(R.id.card_label);
        mOverflowButton = requireViewById(R.id.menu_btn);
        mOverflowAdapter = new ArrayAdapter<>(context, R.layout.wallet_more_item);
        mOverflowPopup = createOverflowPopup(context, mOverflowButton);
        mOverflowButton.setOnClickListener(v -> mOverflowPopup.show());
        mErrorView = requireViewById(R.id.error_view);
        mEmptyStateView = requireViewById(R.id.empty_state);
        mIconSizePx = getResources().getDimensionPixelSize(R.dimen.icon_size);
        mInInterpolator =
                AnimationUtils.loadInterpolator(context, android.R.interpolator.fast_out_slow_in);
        mOutInterpolator =
                AnimationUtils.loadInterpolator(context, android.R.interpolator.accelerate_cubic);
        mAnimationTranslationX = mCardCarousel.getCardWidthPx() / 4f;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Forward touch events to card carousel to allow for swiping outside carousel bounds.
        return mCardCarousel.onTouchEvent(event) || super.onTouchEvent(event);
    }

    @Override
    public void onCardScroll(WalletCardViewInfo centerCard, WalletCardViewInfo nextCard,
            float percentDistanceFromCenter) {
        CharSequence centerCardText = centerCard.getText();
        if (!TextUtils.equals(mCenterCardText, centerCardText)) {
            mCenterCardText = centerCardText;
            mCardLabel.setText(centerCardText);
            Drawable icon = centerCard.getIcon();
            if (icon != null) {
                icon.setBounds(0, 0, mIconSizePx, mIconSizePx);
            }
            mCardLabel.setCompoundDrawablesRelative(icon, null, null, null);
        }
        if (TextUtils.equals(centerCardText, nextCard.getText())) {
            mCardLabel.setAlpha(1f);
        } else {
            mCardLabel.setAlpha(percentDistanceFromCenter);
        }
    }

    void showCardCarousel(
            List<WalletCardViewInfo> data, int selectedIndex, OverflowItem[] menuItems) {
        boolean shouldAnimate = mCardCarousel.setData(data, selectedIndex);
        mOverflowAdapter.clear();
        mOverflowAdapter.addAll(menuItems);
        updateOverflowPopupWidth(menuItems);
        mOverflowButton.setVisibility(menuItems.length == 0 ? GONE : VISIBLE);
        mCardCarouselContainer.setVisibility(VISIBLE);
        mErrorView.setVisibility(GONE);
        mEmptyStateView.setVisibility(GONE);
        if (shouldAnimate) {
            mCardLabel.setAlpha(0f);
            mCardLabel.animate().alpha(1f)
                    .setStartDelay(CARD_LABEL_ANIM_DELAY)
                    .setDuration(CARD_ANIM_ALPHA_DURATION)
                    .start();
            mOverflowButton.setAlpha(0f);
            mOverflowButton.animate().alpha(1f)
                    .setStartDelay(CARD_LABEL_ANIM_DELAY)
                    .setDuration(CARD_ANIM_ALPHA_DURATION)
                    .start();
            mCardCarousel.setTranslationX(mAnimationTranslationX);
            mCardCarousel.animate().translationX(0)
                    .setInterpolator(mInInterpolator)
                    .setDuration(CAROUSEL_IN_ANIMATION_DURATION)
                    .start();
        }
    }

    void animateDismissal() {
        if (mCardCarouselContainer.getVisibility() != VISIBLE) {
            return;
        }
        mOverflowPopup.dismiss();
        mCardCarousel.animate().translationX(mAnimationTranslationX)
                .setInterpolator(mOutInterpolator)
                .setDuration(CAROUSEL_OUT_ANIMATION_DURATION)
                .start();
        mCardCarouselContainer.animate()
                .alpha(0f)
                .setDuration(CARD_ANIM_ALPHA_DURATION)
                .setStartDelay(CARD_ANIM_ALPHA_DELAY)
                .start();
    }

    void showEmptyStateView(Drawable logo, CharSequence logoContentDescription, CharSequence label,
            OnClickListener clickListener) {
        mEmptyStateView.setVisibility(VISIBLE);
        mErrorView.setVisibility(GONE);
        mCardCarouselContainer.setVisibility(GONE);
        ImageView logoView = mEmptyStateView.requireViewById(R.id.icon);
        logoView.setImageDrawable(logo);
        logoView.setContentDescription(logoContentDescription);
        mEmptyStateView.<TextView>requireViewById(R.id.title).setText(label);
        mEmptyStateView.setOnClickListener(clickListener);
    }

    void showDeviceLockedMessage() {
        showErrorMessage(getResources().getText(R.string.error_user_locked));
    }

    void showErrorMessage(@Nullable CharSequence message) {
        if (TextUtils.isEmpty(message)) {
            message = getResources().getText(R.string.error_generic);
        }
        mErrorView.setText(message);
        mErrorView.setVisibility(VISIBLE);
        mCardCarouselContainer.setVisibility(GONE);
        mEmptyStateView.setVisibility(GONE);
    }

    static class OverflowItem {
        final CharSequence label;
        final Runnable onClickListener;

        OverflowItem(CharSequence label, Runnable onClickListener) {
            this.label = label;
            this.onClickListener = onClickListener;
        }

        @Override
        public String toString() {
            return label.toString();
        }
    }

    private ListPopupWindow createOverflowPopup(Context context, View overflowButton) {
        ListPopupWindow popup = new ListPopupWindow(
                new ContextThemeWrapper(context, R.style.Wallet_ListPopupWindow));
        popup.setWindowLayoutType(WindowManager.LayoutParams.TYPE_VOLUME_OVERLAY);
        popup.setAnchorView(overflowButton);
        popup.setAdapter(mOverflowAdapter);
        popup.setModal(true);
        popup.setOnItemClickListener((parent, view, position, id) -> {
            mOverflowAdapter.getItem(position).onClickListener.run();
            popup.dismiss();
        });
        return popup;
    }

    private void updateOverflowPopupWidth(OverflowItem[] overflowItems) {
        int itemWidth = getMaxOverflowItemWidth(overflowItems);
        int itemPadding = getResources().getDimensionPixelSize(R.dimen.wallet_more_padding) * 2;
        mOverflowPopup.setContentWidth(itemWidth + itemPadding);
    }

    private int getMaxOverflowItemWidth(OverflowItem[] overflowItems) {
        Paint paint = new Paint();
        float textSize = getResources().getDimension(R.dimen.wallet_text_size);
        paint.setTextSize(textSize);
        float maxWidth = 0;
        for (OverflowItem item : overflowItems) {
            maxWidth = Math.max(maxWidth, paint.measureText(item.toString()));
        }
        return Math.round(maxWidth);
    }

    void hideErrorMessage() {
        mErrorView.setVisibility(GONE);
    }

    int getIconSizePx() {
        return mIconSizePx;
    }

    WalletCardCarousel getCardCarousel() {
        return mCardCarousel;
    }

    @VisibleForTesting
    TextView getErrorView() {
        return mErrorView;
    }

    @VisibleForTesting
    ViewGroup getEmptyStateView() {
        return mEmptyStateView;
    }

    @VisibleForTesting
    ViewGroup getCardCarouselContainer() {
        return mCardCarouselContainer;
    }

    @VisibleForTesting
    View getOverflowIcon() {
        return mOverflowButton;
    }

    @VisibleForTesting
    ListPopupWindow getOverflowPopup() {
        return mOverflowPopup;
    }
}
