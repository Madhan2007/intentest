/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-10
 * Description: Named keys for the OPZHUB application catalog and company rows.
 */
package com.managemyopz.modules.apps.data;

/** Defines OPZHUB application entity, column, and state names. */
public final class AppsDataConstants {

    public static final String DATABASE_NAME = "OPZHUB";
    public static final String CATALOG_ENTITY = "application_catalog";
    public static final String COMPANY_APPLICATION_ENTITY = "company_application";
    public static final String COMPANY_ENTITY = "company_information";

    public static final String ID_KEY = "id";
    public static final String APP_KEY = "app_key";
    public static final String PRODUCT_CODE_KEY = "product_code";
    public static final String CATEGORY_KEY = "category";
    public static final String DISPLAY_NAME_KEY = "display_name";
    public static final String DESCRIPTION_KEY = "description";
    public static final String ICON_KEY = "icon_key";
    public static final String ICON_BACKGROUND_COLOR_KEY = "icon_background_color";
    public static final String SORT_ORDER_KEY = "sort_order";
    public static final String DETAILS_KEY = "details";
    public static final String COMPANY_ID_KEY = "company_id";
    public static final String APPLICATION_ID_KEY = "application_id";
    public static final String LICENSE_STATE_KEY = "license_state";
    public static final String FAVOURITE_KEY = "favourite";

    public static final String STATE_UNLICENSED = "unlicensed";
    public static final String STATE_LICENSED = "licensed";
    public static final String STATE_INSTALLED = "installed";

    public static final String SEED_RESOURCE = "classpath:modules/apps/db/seed/application_catalog.yaml";
    public static final String SEED_APPLICATIONS_KEY = "applications";

    /** Product catalog (doc 35 §3) — vendor-wide, not module-owned. Backfills
     *  {@link #PRODUCT_KEY} on application_catalog after the main seed loop. */
    public static final String PRODUCTS_CATALOG_RESOURCE = "classpath:platform/catalog/products.yaml";
    public static final String PRODUCTS_KEY = "products";
    public static final String PRODUCT_KEY = "product_key";
    public static final String PRODUCT_APPS_KEY = "apps";

    public static final int CATALOG_PAGE_SIZE = 100;

    private AppsDataConstants() {
    }
}
