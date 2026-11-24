package com.liuzhihang.doc.view.ui;

import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy;
import org.jetbrains.annotations.NotNull;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.List;

final class PopupToolbarLayout {
    private PopupToolbarLayout() {}

    static void keepPreferredMinimumSize(@NotNull ActionToolbar toolbar) {
        ToolbarLayoutStrategy delegate = toolbar.getLayoutStrategy();
        toolbar.setLayoutStrategy(new ToolbarLayoutStrategy() {
            @Override
            public @NotNull List<Rectangle> calculateBounds(@NotNull ActionToolbar actionToolbar) {
                return delegate.calculateBounds(actionToolbar);
            }

            @Override
            public @NotNull Dimension calcPreferredSize(@NotNull ActionToolbar actionToolbar) {
                return delegate.calcPreferredSize(actionToolbar);
            }

            @Override
            public @NotNull Dimension calcMinimumSize(@NotNull ActionToolbar actionToolbar) {
                return delegate.calcPreferredSize(actionToolbar);
            }
        });
    }
}
