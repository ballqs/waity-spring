package com.projectw.common.enums;

import com.projectw.domain.store.dto.response.CategoryMapper;
import com.projectw.domain.store.dto.response.CategoryMapperValue;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public enum Category implements CategoryMapper {
    /*---------------------------------------------------------*/
    ROOT("","", null),
        FOOD("음식종류","FT",ROOT),
            KOREAN_FOOD("한식","1", FOOD),
            JAPANEASE_FOOD("일식","2", FOOD),
    /*---------------------------------------------------------*/
        REGION("지역", "RE", ROOT),
    ;
    /*---------------------------------------------------------*/

    private final String name;
    private final String code;
    private final String path;
    private final int depth;
    @Getter
    private final Category parent;
    @Getter
    private final List<Category> children;
    @Getter
    private boolean rootNode;

    Category(String name, String code, Category parent){
        this.name = name;
        this.parent = parent;
        children = new ArrayList<>();

        // root
        if(parent == null){
            depth = 0;
            rootNode = true;
            this.code = code;
            this.path = "";
        } else {
            depth = 1 + parent.depth;
            parent.getChildren().add(this);

            if(depth != 1) {
                this.code = parent.getCode() + "-" + code;
                this.path = parent.getPath() + "/" + name;
            } else {
                this.code = code;
                this.path = name;
            }
        }
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getPath() {
        if(parent == null) return name;

        return path;
    }

    @Override
    public int getDepth() {
        return depth;
    }

    /**
     * 최상위 카테고리를 가져옵니다.
     * @param node
     * @return
     */
    public static Category getRootCategory(Category node) {
        if(node == ROOT || node.getParent() == ROOT) {
            return node;
        }

        return getRootCategory(node.getParent());
    }

    /**
     * 리프 카테고리인지
     * @return
     */
    public boolean isLeaf() {
        return children.isEmpty();
    }

    /**
     * 루트 카테고리인지
     * @return
     */
    public boolean isRoot() {
        return this == ROOT || getParent() == ROOT;
    }

    /**
     * 해당 노드의 하위 카테고리 전부 가져옵니다.
     * @param node 루트 노드
     * @return
     */
    public static List<Category> getChildCategories(Category node){
        List<Category> childCategories = new ArrayList<>();
        traverse(node, childCategories::add);
        return childCategories;
    }

    /**
     * 해당 depth의 카테고리를 전부 가져옵니다.
     * @param depth
     * @return
     */
    public static List<Category> getCategoriesByDepth(int depth) {
        List<Category> categories = new ArrayList<>();
        Category[] values = values();
        for (Category category : values) {
            if(category.depth == depth) {
                categories.add(category);
            }
        }
        return categories;
    }

    /**
     * 해당 카테고리의 하위 카테고리 내에서 이름으로 검색
     * @param root
     * @param name
     * @return
     */
    public static Optional<Category> findByName(Category root, CategoryMapperValue name) {
        List<Category> childCategories = getChildCategories(root);
        for (Category category : childCategories) {
            if(category.getName().equals(name)) {
                return Optional.of(category);
            }
        }

        return Optional.empty();
    }

    /**
     * DFS
     * @param node 루트 노드
     * @param func 수행할 함수
     */
    private static void traverse(Category node, Consumer<Category> func) {
        if(node == null) return;

        for (Category child : node.getChildren()) {
            func.accept(child);
            traverse(child, func);
        }
    }
}
