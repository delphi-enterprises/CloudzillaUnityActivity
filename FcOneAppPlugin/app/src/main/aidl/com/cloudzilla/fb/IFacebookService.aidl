/* Copyright (c) 2013 Cloudzilla Inc. All Rights Reserved.
 *
 * This software is the confidential and proprietary information of
 * Cloudzilla Inc. ("Confidential Information").  You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Cloudzilla Inc.
 *
 * CLOUDZILLA INC. MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE
 * SUITABILITY OF THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING
 * BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE, OR NON-INFRINGEMENT. CLOUDZILLA
 * INC. WILL NOT BE LIABLE FOR ANY DAMAGES SUFFERED BY LICENSEE AS A
 * RESULT OF USING, MODIFYING OR DISTRIBUTING THIS SOFTWARE OR ITS
 * DERIVATIVES.
 */

package com.cloudzilla.fb;

import android.app.PendingIntent;


/**
 * IFacebookService provides integration features for when the app is
 * run on Facebook.
 */
interface IFacebookService {
    /**
     * Checks if the app is run on Facebook.
     *
     * @param apiVersion the Facebook integration version that the app
     *                   is using. Current version is 1.
     * @return OK(0) on success, otherwise WRONG_VERSION(1), NOT_ON_FACEBOOK(2).
     * @throws android.os.RemoteException if an error occurs
     */
    int isOnFacebook(int apiVersion);

    /* *************************************************************************
     * Authentication methods.
     * ************************************************************************/

    /**
     * @return the Facebook application ID.
     * @throws android.os.RemoteException if an error occurs
     */
    String getApplicationId();

    /**
     * @return the Facebook user ID.
     * @throws android.os.RemoteException if an error occurs
     */
    String getUserId();

    /**
     * @return the auhentication token that can be used to log in on
     *         Facebook as the user.
     * @throws android.os.RemoteException if an error occurs
     */
    String getAccessToken();

    /**
     * @return the FB permissions associated with the current access
     *         token.
     * @throws android.os.RemoteException if an error occurs
     */
    List<String> getPermissions();

    /* *************************************************************************
     * Advertisement methods.
     * ************************************************************************/

    /**
     * Returns a pending intent to show an Advertisement to the user.
     *
     * @return PendingIntent to display the Advertisement. The Pending
     *         intent should be launched with {@link
     *         android.app.Activity#startIntentSenderForResult}. When the
     *         advertisement flow has completed, the
     *         onActivityResult() will give a resultCode of OK(0),
     *         ERROR(3), or USER_CANCELED(4).
     *
     * @throws android.os.RemoteException if an error occurs
     */
    PendingIntent getAdvertisementIntent();

    /* *************************************************************************
     * JavaScript Facebook SDK methods. See
     * https://developers.facebook.com/docs/javascript/reference
     * ************************************************************************/

    /**
     * Calls FB.ui() on user's browser.
     *
     * @param params stringified JSON containing the ui arguments
     * @return the stringified JSON response
     * @throws android.os.RemoteException if an error occurs
     * @see <a href="https://developers.facebook.com/docs/javascript/reference/FB.ui">FB.ui</a>
     */
    String ui(String params);

    /**
     * Call FB.api() on the user's browser.
     *
     * @param path the Graph API endpoint
     * @param method the http method to use ("get", "post", or "delete")
     * @param params stringified JSON containing the methods arguments
     * @return the stringified JSON response
     * @throws android.os.RemoteException if an error occurs
     * @see <a href="https://developers.facebook.com/docs/javascript/reference/FB.api">FB.api</a>
     */
    String api(String path, String method, String params);

    /**
     * Call FB.login() on the user's browser.
     *
     * @param opts stringified JSON containing the login arguments
     * @return the stringified JSON response
     * @throws android.os.RemoteException if an error occurs
     * @see <a href="https://developers.facebook.com/docs/reference/javascript/FB.login">FB.login</a>
     */
    String login(String opts);

    /* *************************************************************************
     * Advertisement methods.
     * ************************************************************************/

    /**
     * Returns a pending intent to show an Offer Wall to the user.
     *
     * @param virtualCurrencyId identifies the virtual currency that will be
     *        loaded in the Offer Wall.
     *
     * @param offerwallId identifies individual Offer Wall
     *        integrations. Unique values are required if you are
     *        integrating more than one Offer Wall into your app.
     *
     * @return PendingIntent to display the Offer Wall. The Pending
     *         intent should be launched with {@link
     *         android.app.Activity#startIntentSenderForResult}. When
     *         the Offer Wall flow has completed, the
     *         onActivityResult() will give a resultCode of OK(0) or
     *         ERROR(3).
     * @throws android.os.RemoteException if an error occurs
     */
    PendingIntent getOfferWallIntent(String virtualCurrencyId,
                                     int offerwallId);

    /**
     * Returns the user's balance for a given virtual currency.
     *
     * @param virtualCurrencyId identifies the virtual currency for
     *        which the balance is requested.
     *
     * @return The amount of virtual currency owned by the user. A
     *         negative result indicates an error.
     * @throws android.os.RemoteException if an error occurs
     */
    int getVirtualCurrencyBalance(String virtualCurrencyId);

    /**
     * Decrease the user's balance for a given virtual currency by a
     * given amount.
     *
     * @param virtualCurrencyId identifies the virtual currency for which the
     *        decreased balance is requested.
     *
     * @param amount indicates the requested decrease.
     *
     * @return The actual amount of virtual currency spent. It is
     *         different from the requested quantity in case of
     *         error.
     * @throws android.os.RemoteException if an error occurs
     */
    int spendVirtualCurrency(String virtualCurrencyId, int amount);

    /**
     * Increase the user's balance for a given virtual currency by a
     * given amount.
     *
     * @param virtualCurrencyId identifies the virtual currency for which the
     *        decreased balance is requested.

     * @param amount indicates the requested increase.
     *
     * @return The actual amount of virtual currency awarded. It is
     *         different from the requested quantity in case of
     *         error.
     * @throws android.os.RemoteException if an error occurs
     */
    int awardVirtualCurrency(String virtualCurrencyId, int amount);

    /* These methods relate to FB access token and are reported below
     * only for compatibility reasons.
     */

    long getAccessTokenExpirationTimeInMs();
    long getAccessTokenLastRefreshTimeInMs();
}
