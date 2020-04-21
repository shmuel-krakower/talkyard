/// <reference path="./test-types2.ts" />
/// <reference path="./pub-api.ts" />


// Unique hostname for the current test run, so sites won't overwrite each other.
declare const thisSpecLocalHostname: string | undefined;
declare function getCidOrDie(): string;


// ----- Test framework

// This results in a weird outside-the-project error:
//xx <reference path="../../../modules/definitely-typed/types/mocha/index.d.ts"/>
// instead:
declare const it: any;
declare const describe: any;


// ----- WebdriverIO

// It's unclear if 'browser' refers to an instance of TyE2eTestBrowser
// or WebdriverIO.BrowserObject, so let's avoid that name.
declare const wdioBrowser: WebdriverIO.BrowserObject;  // RENAME to allWdioBrowsers
declare const oneWdioBrowser: WebdriverIO.BrowserObject;
declare const wdioBrowserA: WebdriverIO.BrowserObject;
declare const wdioBrowserB: WebdriverIO.BrowserObject | U;
declare const wdioBrowserC: WebdriverIO.BrowserObject | U;

// Rename to  wdioBrowserA  instead:
declare const browserA: WebdriverIO.BrowserObject;
declare const browserB: WebdriverIO.BrowserObject | U;
declare const browserC: WebdriverIO.BrowserObject | U;

type SelectorOrElem = string | WebdriverIO.Element;

const enum IsWhere {
  Forum = 1,
  LoginPopup = 2,

  EmbFirst = 3,
  EmbeddingPage = 3,
  EmbCommentsIframe = 4,
  EmbEditorIframe = 5,
  EmbLast = 5,

  // Another server, e.g. Google's OAuth login page. But not an
  // embedding blog post page.
  External = 10,
}

