/// <reference path="../test-types.ts"/>

import * as _ from 'lodash';
import assert = require('../utils/ty-assert');
import server = require('../utils/server');
import utils = require('../utils/utils');
import { buildSite } from '../utils/site-builder';
import pagesFor = require('../utils/pages-for');
import settings = require('../utils/settings');
import lad = require('../utils/log-and-die');
import c = require('../test-constants');

declare var browser: any;
declare var browserA: any;
declare var browserB: any;

let richBrowserA;
let richBrowserB;
let owen: Member;
let owensBrowser;
let charliesBrowser;
let chumasBrowser;
let strangersBrowser;

let siteIdAddress: IdAddress;
let siteId;

let forum: TwoPagesTestForum;

const apiSecret: TestApiSecret = {
  nr: 1,
  userId: c.SysbotUserId,
  createdAt: c.MinUnixMillis,
  deletedAt: undefined,
  isDeleted: false,
  secretKey: 'publicE2eTestSecretKeyDefg345',
};

const ssoDummyLoginSlug = 'sso-dummy-chatters-login.html';
const ssoUrl =
    `http://localhost:8080/${ssoDummyLoginSlug}?returnPath=\${talkyardPathQueryEscHash}`;


const owensSsoId = 'owensSsoId';

const chumaExtUser: ExternalUser = {
  ssoId: "Chuma's SSO id",
  username: 'chuma_un',
  fullName: 'Chuma Ext User',
  primaryEmailAddress: 'e2e-test-chuma@x.co',
  isEmailAddressVerified: true,
};

const charlieExtUser: ExternalUser = {
  ssoId: "Charlie's SSO id",
  extId: "Charlie's External id",
  username: 'charlie_un',
  fullName: 'Charlie Ext User',
  primaryEmailAddress: 'e2e-test-charlie@x.co',
  isEmailAddressVerified: true,
};


const categoryExtId = 'chat_cat_ext_id';

const chatPageOne = {
  extId: 'chat_page_one_ext_id',
  pageType: c.TestPageRole.PrivateChat,
  categoryRef: `extid:${categoryExtId}`,
  authorRef: `ssoid:${chumaExtUser.ssoId}`,  // try with sso id ... (5393267)
  title: 'chatPageOne title',
  body: 'chatPageOne body',
  pageMemberRefs: [
    // Test w both ssoid and extid.
    `ssoid:${chumaExtUser.ssoId}`,
    `extid:${charlieExtUser.extId}`]
};

const chumaSaysHiCharlie = {
  extId: 'chumaSaysHiCharlie extId',
  postType: c.TestPostType.ChatMessage,
  parentNr: c.BodyNr,
  pageRef: `extid:${chatPageOne.extId}`,
  authorRef: `ssoid:${chumaExtUser.ssoId}`,   // ... (sso id here too)
  body: 'chumaSaysHiCharlie body',
};

const charlieSaysHiChuma = {
  ...chumaSaysHiCharlie,
  extId: 'charlieSaysHiChuma extId',
  authorRef: `extid:${charlieExtUser.extId}`, // ... and ext id too  (5393267)
  body: 'charlieSaysHiChuma body',
};

const chumaRepliesToCharlie = {
  ...chumaSaysHiCharlie,
  extId: 'chumaRepliesToCharlie extId',
  authorRef: `ssoid:${chumaExtUser.ssoId}`,
  body: 'chumaSaysHiCharlie body',
};




describe("api-private-chat-two-pps-notfs   TyT603WKVJW336", () => {

  if (settings.prod) {
    console.log("Skipping this spec — the server needs to have upsert conf vals enabled."); // E2EBUG
    return;
  }


  // ----- Create site, with API enabled

  it("import a site", () => {
    const builder = buildSite();
    forum = builder.addTwoPagesForum({
      categoryExtId,  // instead of: [05KUDTEDW24]
      title: "Api Priv Chat 2 Participants E2E Test",
      // But Chuma and Charlie are the users active in this test (SSO-created later).
      members: ['owen', 'maria', 'michael'],
    });
    assert.refEq(builder.getSite(), forum.siteData);
    const site: SiteData2 = forum.siteData;
    site.settings.enableApi = true;
    site.apiSecrets = [apiSecret];

    site.settings.ssoUrl = ssoUrl;
    site.settings.enableSso = true;

    siteIdAddress = server.importSiteData(forum.siteData);
    siteId = siteIdAddress.id;
    server.skipRateLimits(siteId);
  });

  it("initialize people", () => {
    richBrowserA = _.assign(browserA, pagesFor(browserA));
    richBrowserB = _.assign(browserB, pagesFor(browserB));

    owen = forum.members.owen;
    owensBrowser = richBrowserA;
    charliesBrowser = richBrowserA;
    chumasBrowser = richBrowserB;
  });


  let charliesOneTimeLoginSecret: string;

  it("The remote server upserts Charlie", () => {
    charliesOneTimeLoginSecret = server.apiV0.upsertUserGetLoginSecret({
        origin: siteIdAddress.origin,
        apiRequesterId: c.SysbotUserId,
        apiSecret: apiSecret.secretKey,
        externalUser: charlieExtUser });
  });

  let chumasOneTimeLoginSecret: string;

  it("The remote server upserts Chuma", () => {
    chumasOneTimeLoginSecret = server.apiV0.upsertUserGetLoginSecret({
        origin: siteIdAddress.origin,
        apiRequesterId: c.SysbotUserId,
        apiSecret: apiSecret.secretKey,
        externalUser: chumaExtUser });
  });

  it("Chuma logs in", () => {
    chumasBrowser.apiV0.loginWithSecret({
        origin: siteIdAddress.origin,
        oneTimeSecret: chumasOneTimeLoginSecret,
        thenGoTo: '/' });
  });



  let owensOneTimeLoginSecret;

  it("The remote server generates a login secret for Owen", () => {
    const owenExtUser = utils.makeExternalUserFor(owen, { ssoId: owensSsoId });
    owensOneTimeLoginSecret = server.apiV0.upsertUserGetLoginSecret({
        origin: siteIdAddress.origin,
        apiRequesterId: c.SysbotUserId,
        apiSecret: apiSecret.secretKey,
        externalUser: owenExtUser });
  });

  /*
  it("... Owen logs in", () => {
    owensBrowser.apiV0.loginWithSecret({
        origin: siteIdAddress.origin,
        oneTimeSecret: owensOneTimeLoginSecret,
        thenGoTo: '/' });
  }); */


  // ----- API upsert chat page incl first message

  let upsertResponse;

  it("Chuma sends a messag to Charlie, via an external server and the API", () => {
    upsertResponse = server.apiV0.upsertSimple({
      origin: siteIdAddress.origin,
      apiRequesterId: c.SysbotUserId,
      apiSecret: apiSecret.secretKey,
      data: {
        upsertOptions: { sendNotifications: true },
        pages: [chatPageOne],
        posts: [chumaSaysHiCharlie],
      },
    });
  });

  let firstUpsertedPage: any;

  it("... gets back the upserted page in the server's response", () => {
    console.log("Page ups resp:\n\n:" + JSON.stringify(upsertResponse, undefined, 2));

    assert.eq(upsertResponse.pages.length, 1);
    firstUpsertedPage = upsertResponse.pages[0];

    assert.eq(firstUpsertedPage.urlPaths.canonical, '/-1/chatpageone-title');

    assert.eq(firstUpsertedPage.id, "1");
    assert.eq(firstUpsertedPage.pageType, c.TestPageRole.PrivateChat);
    utils.checkNewPageFields(firstUpsertedPage, {
      categoryId: forum.categories.specificCategory.id,
      numPostsTotal: 3,  // title, body, 1st chat message
      // authorId: ??
    });

    // utils.checkNewPostFields(firstUpsertedPage, {
    // });
  });


  it("Charlie gets an email notification about the chat message", () => {
    // COULD incl the chat *message* in the notification, [PATCHNOTF]
    // not the page title and body.
    server.waitUntilLastEmailMatches(
        siteIdAddress.id, charlieExtUser.primaryEmailAddress,
        ['charlie', chatPageOne.title, chatPageOne.body], browserA);
  });

  let prevNumEmailsSent = 0;

  it("But no one else", () => {
    const { num, addrsByTimeAsc } = server.getEmailsSentToAddrs(siteId);
    assert.eq(num, prevNumEmailsSent + 1, `Emails sent to: ${addrsByTimeAsc}`);
    prevNumEmailsSent = num;
  });



  it("Charlie replies to Chuma: Upserts page again, + new message", () => {
    upsertResponse = server.apiV0.upsertSimple({
      origin: siteIdAddress.origin,
      apiRequesterId: c.SysbotUserId,
      apiSecret: apiSecret.secretKey,
      data: {
        upsertOptions: { sendNotifications: true },
        pages: [chatPageOne],
        posts: [charlieSaysHiChuma],
      },
    });
  });

  let chumasNotfLink: string;

  it("Chuma gets an email notification, remembers the link", () => {
    const emailMatchResult: EmailMatchResult = server.waitUntilLastEmailMatches(
        siteIdAddress.id, chumaExtUser.primaryEmailAddress,
        ['chuma', charlieSaysHiChuma.body], browserA);
    chumasNotfLink = utils.findAnyFirstLinkToUrlIn(
        siteIdAddress.origin, emailMatchResult.matchedEmail.bodyHtmlText);
    lad.logMessage(`Chuma's notification link: ${chumasNotfLink}`);
  });

  it("But no one else", () => {
    const { num, addrsByTimeAsc } = server.getEmailsSentToAddrs(siteId);
    assert.eq(num, prevNumEmailsSent + 1, `Emails sent to: ${addrsByTimeAsc}`);
    prevNumEmailsSent = num;
  });



  it("Chuma replies again: Upserts only a message (not the page)", () => {
    upsertResponse = server.apiV0.upsertSimple({
      origin: siteIdAddress.origin,
      apiRequesterId: c.SysbotUserId,
      apiSecret: apiSecret.secretKey,
      data: {
        upsertOptions: { sendNotifications: true },
        // pages: [chatPageOne],  — skip
        posts: [chumaRepliesToCharlie],
      },
    });
  });

  let charliesNotfEmail: EmailSubjectBody;
  let charliesNotfLink: string;

  it("Charlie gets an email notification, because is private chat page", () => {
    charliesNotfEmail = server.waitUntilLastEmailMatches(
        siteIdAddress.id, charlieExtUser.primaryEmailAddress,
        ['charlie', chumaRepliesToCharlie.body], browserA).matchedEmail;
  });

  it("... he remembers the notf link", () => {
    charliesNotfLink = utils.findAnyFirstLinkToUrlIn(
        siteIdAddress.origin, charliesNotfEmail.bodyHtmlText);
    lad.logMessage(`Charlies's notification link: ${chumasNotfLink}`);
  });

  it("But no one else", () => {
    const { num, addrsByTimeAsc } = server.getEmailsSentToAddrs(siteId);
    assert.eq(num, prevNumEmailsSent + 1, `Emails sent to: ${addrsByTimeAsc}`);
    prevNumEmailsSent = num;
  });


  it("Charlies opens the notification link", () => {
    charliesBrowser.go2(charliesNotfLink);
  });


  it("... but he's not logged in", () => {
    charliesBrowser.assertNotFoundError();
  });


  it("Charlie logs in via his one-time login link", () => {
    charliesBrowser.apiV0.loginWithSecret({
        origin: siteIdAddress.origin,
        oneTimeSecret: charliesOneTimeLoginSecret,
        thenGoTo: '/' });
  });


  it("... opens the notification link again", () => {
    charliesBrowser.go2(charliesNotfLink);
  });


  it("... now sees Chuma's message", () => {
    charliesBrowser.topic.waitForPostAssertTextMatches(
        c.FirstReplyNr + 2, chumaRepliesToCharlie.body);
  });


  // ----- The upserted page works: Can post replies via Ty's interface, not only API

  it("Charlie posts a message", () => {
    charliesBrowser.chat.addChatMessage(
        `This works just like ducks digging for bucks`);
  });

  it("... Chuma gets notified", () => {
    server.waitUntilLastEmailMatches(
        siteIdAddress.id, chumaExtUser.primaryEmailAddress,
        [chumaExtUser.username, 'ducks digging for bucks'], browserA);
  });

  it("But no one else", () => {
    const { num, addrsByTimeAsc } = server.getEmailsSentToAddrs(siteId);
    assert.eq(num, prevNumEmailsSent + 1, `Emails sent to: ${addrsByTimeAsc}`);
    prevNumEmailsSent = num;
  });

});

