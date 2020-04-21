/**
 * Copyright (C) 2011-2013 Kaj Magnus Lindberg (born 1979)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.debiki.core

import java.{util => ju}
import java.{security => js}
import org.apache.commons.codec.{binary => acb}
import org.scalactic.{ErrorMessage, Or}
import play.api.libs.json.{JsNumber, JsObject, JsString}
import scala.collection.mutable
import scala.util.Try
import scala.util.matching.Regex
import scala.collection.immutable


object Prelude {


  // Logs an error in release mode, but throws an AssertionError in debug
  // mode -- this makes errors obvious during development and recoverable
  // in production mode. [Code Complete p. 206: Use Offensive Programming]
  // The caller should fail gracefully (e.g. use a reasonable fallback
  // instead of the corrupted database data).
  // CLEAN_UP COULD move to some DebikiLogger class and check if we're in debug
  // or release mode.

  // Should get rid of this version:
  def warnDbgDie(errorMsg: String) { warnDbgDie("", errorMsg) }

  def warnDbgDie(errorCode: String, warningMsg: String) {
    if (true) {
      // Fail hard in debug mode so this error will be fixed.
      throw new AssertionError(warningMsg)
    }
    else {
      // Only log a warning in release mode.
    }
  }

  // Should get rid of this version:
  def errDbgDie(errorMsg: String) { errDbgDie("", errorMsg) }

  def errDbgDie(errorCode: String, errorMsg: String) {
    if (true) {
      throw new AssertionError(errorMsg)
    }
    else {
      // Log error
    }
  }


  def stringifyExceptionAndCauses(ex: Exception): String = {
    var message = ex.getMessage
    if (message eq null) message = "(No exception message)"
    var currentCause = ex.getCause
    val seenCauses = mutable.ArrayBuffer[Object]()
    var count = 0
    while ((currentCause ne null) && !seenCauses.exists(_ eq currentCause) &&
        // Extra safety, in case 'eq' above won't work:
        count < 20) {
      message += "\nCaused by: " + currentCause.getMessage
      count += 1
      seenCauses.append(currentCause)
      currentCause = currentCause.getCause
    }
    message
  }


  /** Converts from a perhaps-{@code null} reference to an {@code Option}.
   */
  def ?[A <: AnyRef](x: A): Option[A] = if (x eq null) None else Some(x)

  import java.lang.{UnsupportedOperationException => UOE}

  implicit class GetOrDie[A](val underlying: Option[A]) {
    def getOrDie(errorCode: String, message: => String = ""): A = underlying.getOrElse(
      throw new ju.NoSuchElementException(
        if (message.nonEmpty) s"$message [$errorCode]"
        else s"Element missing: None.get [$errorCode]"))
  }

  implicit class GetOrDieMap[K, V](val underlying: Map[K, V]) {
    def getOrDie(key: K, errorCode: String, message: => String = ""): V = underlying.getOrElse(key,
      throw new ju.NoSuchElementException(
        if (message.nonEmpty) s"$message [$errorCode]"
        else s"Element missing: Map.get [$errorCode]"))
  }

  // Error codes should be formatted like so:
  // "DwE<number><alnum x 3><number>", e.g. "DwE8kR32".

  def unsupported = throw new UOE
  def unsupported(what: String) = throw new UOE(what)
  def unsupported(what: String, errorCode: String) =
    throw new UOE(s"$what [$errorCode]")
  def unimplemented = throw new UOE("Not implemented")
  def unimplemented(what: String) = throw new UOE("Not implemented: "+ what)
  def unimplemented(what: String, errorCode: String) =
    throw new UOE(s"Not implemented: $what [$errorCode]")
  def unimplementedIf(condition: Boolean, what: String): Unit =
    if (condition) unimplemented(what)

  /** Useful code but currently not in use. Abort, so I'll notice, and test it again before
    * starting using it again.
    */
  def unused(errorCode: String, what: => String = "") =
    throw new UOE(s"Not in use: $what [$errorCode]")

  def untested(errorCode: String, what: => String = "") =
    throw new UOE(s"Not tested: $what [$errorCode]")

  def untestedIf(condition: Boolean, errorCode: String, what: => String = ""): Unit =
    if (condition) untested(errorCode, what)

  def throwNoSuchElem(errorCode: String, message: => String) =
    throw new NoSuchElementException(s"$message [$errorCode]")

  def die(errorCode: String, problem: => String = null, cause: => Throwable = null): Nothing = {
    // Don't throw AssertionError — that makes things like Akka's actor system shutdown
    // and the server becomes a zombie server (half dead).
    throw new RuntimeException(formatErrorMessage(errorCode, problem), cause)
  }

  def requireIf(condition: Boolean, test: => Boolean, message: => String) {
    if (condition) {
      require(test, message)
    }
  }


  private def formatErrorMessage(errorCode: String, details: String) =
      (if ((details eq null) || details.isEmpty) "" else details + " ") + s"[$errorCode]"

  def dieIf(condition: Boolean, errorCode: String, problem: => Any = null): Unit =
    if (condition) die(errorCode, if (problem != null) problem.toString else null)

  def dieIfAny[T](things: Iterable[T], condition: T => Boolean,
        errorCode: String, problem: T => Any = null) {
    things.find(condition) foreach { badThing =>
      val details = if (problem ne null) problem(badThing).toString else null
      die(errorCode, details)
    }
  }

  def dieUnless(condition: Boolean, errorCode: String, problem: => String = null): Unit =
    if (!condition) die(errorCode, problem)

  def dieIfBad[A](value: A Or ErrorMessage, errorCode: String, mkMessage: ErrorMessage => String = null)
        : Unit =
    if (value.isBad) die(errorCode, if (mkMessage ne null) mkMessage(value.swap.get) else null)

  def throwIllegalArgumentIf(condition: Boolean, errorCode: String,
        problem: => String = null) {
    if (condition)
      illArgErr(errorCode, problem)
  }

  def throwIllegalArgument(errorCode: String, problem: => String = null): Nothing =
    illArgErr(errorCode, problem)

  @deprecated("now", "use throwIllegalArgument() instead")
  def illArgErr(errorCode: String, problem: => String = null): Nothing =
    throw new IllegalArgumentException(formatErrorMessage(errorCode, problem))

  def forbid(condition: Boolean, message: => String): Unit =
    require(!condition, message)

  @deprecated("now", "use forbid() instead")
  def illArgIf(condition: Boolean, errorCode: String, problem: => String = null): Unit =
    if (condition) illArgErr(errorCode, problem)

  @deprecated("now", "use forbid() instead")
  def illArgErrIf(condition: Boolean, errorCode: String, problem: => String): Unit =
    if (condition) illArgErr(errorCode, problem)

  // COULD remove
  @deprecated("now", "use forbid() instead")
  def illArgErrIf3(condition: Boolean, errorCode: String, problem: => String): Unit =
    if (condition) illArgErr(errorCode, problem)

  def throwBadDatabaseData(errorCode: String, problem: => String): Nothing =
    throw new BadDatabaseDataException(formatErrorMessage(errorCode, problem))

  class BadDatabaseDataException(message: String) extends RuntimeException(message)


  /** Converts {@code text} to a single line printable ASCII, not very long,
   *  so it can be included in an error message even if it is end user defined
   *  (i.e. possible destructive were it not made safe).
   */
  def safe(obj: AnyRef): String = {
    val str =
      if (obj eq null) "(null)"
      else if (obj.isInstanceOf[String]) obj.asInstanceOf[String]
      else obj.toString
    str // for now
  }


  // Copied from: http://stackoverflow.com/a/106223/694469
  // Supposedly adheres to http://tools.ietf.org/html/rfc952.
  // I appended "(:\d+)?" for the port number, so e.g. localhost:9000 works.
  private val ValidHostAndPortRegexStr: String =
    """(([a-zA-Z]|[a-zA-Z][a-zA-Z0-9\-]*[a-zA-Z0-9])\.)*([A-Za-z]|[A-Za-z][A-Za-z0-9\-]*[A-Za-z0-9])(:\d+)?"""

  private val ValidHostAndPortRegex: Regex = s"""^$ValidHostAndPortRegexStr$$""".r

  def isValidHostAndPort(hostAndPort: String): Boolean =
    ValidHostAndPortRegex.pattern.matcher(hostAndPort).matches


  def urlIsToDifferentOrigin(url: String, thisServerOrigin: String): Boolean = {
    val urlUri = new java.net.URI(url)

    require(thisServerOrigin.contains("//"),
      s"Not an origin: '$thisServerOrigin' [TyE02WKL62GJ]")

    // Only an URL path? Then it's the same server.
    if (urlUri.getHost == null) {
      assert(urlUri.getScheme == null)
      assert(urlUri.getPort == -1)
      return false
    }

    val thisUri = new java.net.URI(thisServerOrigin)
    if (urlUri.getHost != thisUri.getHost)
      return true

    if (urlUri.getScheme != null && urlUri.getScheme != thisUri.getScheme)
      return true
    // Else: The browser will default to the same scheme, i.e. use thisUri's scheme.

    def getPortOrDefault(uri: java.net.URI): Int = {
      if (uri.getPort != -1) uri.getPort
      else {
        if (uri.getScheme == "http") 80
        else if (uri.getScheme == "https") 443
        else -1
      }
    }

    val urlPort = getPortOrDefault(urlUri)
    val thisPort = getPortOrDefault(thisUri)

    val thisUsesStandardPort =
      (thisPort == 80 && thisUri.getScheme == "http") ||
        (thisPort == 443 && thisUri.getScheme == "https")

    if (urlUri.getScheme == null && urlPort == -1 && thisUsesStandardPort)
      return false  // then will default to same port

    if (urlPort != thisPort)
      return true

    false
  }


  /**
   * Strips "http(s)://server:port" from an URL. Returns None if "htt(s)://server"
   * was absent, or if there was nothing after the origin.
   */
  def stripOrigin(url: String): Option[String] = url match {
    case StripOriginRegex(_, _, path) => Option(path)
    case _ => None
  }

  def stripSchemeSlashSlash(url: String): String =
    url.replaceFirst("https://", "").replaceFirst("http://", "")

  /** Returns '/' if there's no url path or the url is weird. */
  def extractUrlPath(url: String) =
    stripOrigin(url).getOrElse("/").takeWhile(c => c != '#' && c != '&');

  // This should match too much, if something is wrong/weird, rather than too little
  // (so don't use ValidHostAndPortRegexStr).
  private val StripOriginRegex: Regex = "^((https?:)?//[^/]+)?(/.*)$".r

  val GetOriginRegex: Regex = "^(https?://[^/]+).*$".r
  val GetHostnameRegex: Regex = "^https?://([^/:]+)?.*$".r


  // For now, IPv4 only.
  val IsIpAddrRegex: Regex = """\d+\.\d+\.\d+\.\d+(:\d+)?""".r


  /** Like {@code safe}, but wraps the string between start and end
   * *d*elimiters "`" and "'", like so: <i>`the-dangerous-string'</i>
   *  -- unless it's null, then returns "(null)".
   */
  def safed(obj: AnyRef): String =
    if (obj eq null) "(null)" else "`"+ safe(obj) +"'"

  /** Replaces any non-breaking space (i.e. \u00a0) with a real space.
   *  Removes all carriage returns '\r'.
   */
  def convertBadChars(text: String): String = {
    // !! Find any Unicode c2a0 (utf8, 00a0 in utf16) token, i.e. a
    // real non-breaking-space (rather than a &nbsp;). If such a token
    // is inside a template's <head>, the parser ends the <head> right there
    // and starts the <body> instead! I've been troubleshooting this for
    // some hours now.
    // Here is a non-breaking space: ' '. (To verify, e.g. copy it to Vim,
    // place the carret on it and type 'ga' or 'g8', and Vim shows its
    // utf-16 or utf-8 representation (00a0 and c2a0).
    // -- Also remove e.g. form feed? new page? and other weird Unicode tokens?
    text  // for now, COULD implement it (convertBadChars) some day.
  }

  def classNameOf(x: Any): String = (x: @unchecked) match {
    case x: AnyRef => x.getClass.getSimpleName
    case _: Int => "Int"
    case _: Long => "Long"
    case _: Char => "Char"
    case _: Byte => "Byte"
    case _: Any => "Any"
    case null => "null"
  }

  def stripStartEndBlanks(text: String): String = {
    val start = text.indexWhere(_ > ' ')
    if (start == -1) return ""
    var end = text.lastIndexWhere(_ > ' ')
    text.slice(start, end + 1)
  }

  // E.g. "2011-12-03T10:15:30Z". Thread safe.
  private val timeFormatterIsoSecondsUtc =
    java.time.format.DateTimeFormatter.ISO_INSTANT.withZone(
      java.time.ZoneOffset.UTC)

  def toIso8601T(millis: Long): String =
    timeFormatterIsoSecondsUtc.format(java.time.Instant.ofEpochMilli(millis))

  def toIso8601T(date: ju.Date): String = toIso8601T(date.getTime)

  /** Returns the date formatted according to ISO 8601,
    * e.g. "2010-06-23 11:37:15Z", with a space not a 'T' between the
    * date and time.
    */
  def toIso8601NoT(millis: Long): String =
    toIso8601T(millis).replaceAllLiterally("T", " ")

  def toIso8601NoT(date: ju.Date): String =
    toIso8601NoT(date.getTime)


  // E.g. "2011-12-03". Thread safe.
  private val timeFormatterIsoDayUtc =
    java.time.format.DateTimeFormatter.ISO_LOCAL_DATE.withZone(
      java.time.ZoneOffset.UTC)

  def toIso8601Day(millis: Long): String =
    timeFormatterIsoDayUtc.format(java.time.Instant.ofEpochMilli(millis))

  def toIso8601Day(date: ju.Date): String = toIso8601Day(date.getTime)


  /** A date like "2015-12-31 23:59Z", i.e. no T and no seconds.
    *
    * 1) It is permitted, in ISO 8601, to omit the 'T' character by mutual agreement of
    * the partners in information interchange (i.e. this server and the JS code in
    * the browser).
    *
    * 2) Don't add a quote "'" before the 'Z' or moment.js says "Invalid date".
    */
  def toIso8601NoSecondsNoT(date: ju.Date): String = {
    // Don't include 'Z' in the format string, it gets replaced by "+0000". Append later instead.
    // SimpleDateFormat is not thread safe.
    val sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm")
    sdf.setTimeZone(_timezoneUtc)
    sdf.format(date) + "Z"
  }

  def parseIso8601DateTime(dateTime: String): ju.Date = {
    val calendar: ju.Calendar =
       javax.xml.bind.DatatypeConverter.parseDateTime(dateTime)
    val calendarUtc = _convertToUtc(calendar)
    calendarUtc.getTime
  }

  private val _timezoneUtc = ju.TimeZone.getTimeZone("UTC")

  private def _convertToUtc(calendar: ju.Calendar): ju.Calendar = {
    // Create another Calendar, calendarUtc, with time zone UTC (GMT) and
    // add [the time zone offset in milliseconds between `calendarUtc` and
    // `calendar`] to `calendarUtc`.
    val dati: ju.Date = calendar.getTime
    val millis: Long = dati.getTime
    // The offset depends on the date (daylight saving time).
    val timeZone: ju.TimeZone = calendar.getTimeZone
    val offsetFromUtc: Int = timeZone.getOffset(millis)
    val calendarUtc = ju.Calendar.getInstance(ju.TimeZone.getTimeZone("UTC"))
    calendarUtc.setTime(dati)
    calendarUtc.add(ju.Calendar.MILLISECOND, offsetFromUtc)
    calendarUtc
  }

  /* Comment in and test, if needed.
  def anyMax[T <: math.Ordered](a: Option[T], b: Option[T]): Option[T] = {
    if (a.isEmpty && b.isEmpty) None
    else if (a.isEmpty) b
    else if (b.isEmpty) a
    else if (a.get < b.get) b
    else a
  }*/


  def anyMaxDate(a: Option[ju.Date], b: Option[ju.Date]): Option[ju.Date] = {
    if (a.isEmpty && b.isEmpty) None
    else if (a.isEmpty) b
    else if (b.isEmpty) a
    else if (a.get.getTime < b.get.getTime) b
    else a
  }


  // Is thread safe.
  private val _random = new java.security.SecureRandom();

  def nextRandomAzLetter(): Char = ('a' + _random.nextInt(26)).toChar

  def nextRandomLong(min: Int = 0): Long = {
    require(min < Long.MaxValue / 2, "TyE4KGKRY")
    var result = 0L
    do {
      result = _random.nextLong()
    }
    while (result < min)
    result
  }


  /** Generates a 130 bit string, almost 26 chars long since each char in a 32 chars
    * alphabet has 5 bits (but we use 36 chars here).
    * Wikipedia says: "128-bit keys are commonly used and considered very strong".
    * Here: http://en.wikipedia.org/wiki/Key_(cryptography)
    */
  def nextRandomString(): String =
    new java.math.BigInteger(130, _random).toString(36) // 0..9, a..z

  // COULD split this in a random string function, and other id generation
  // functions, with string length adjusted, depending on how the random
  // string will be used.
  def nextRandomPageId(): String = {  // TODO exclude vowels, so no bad words
    var s = nextRandomString()
    // Remove vowels to reduce the possibility of ugly bad words.
    // Keep vowels "uy" though, so there are 32 chars in total.
    // "uy" are the least common vowels.
    s = s filterNot ("aoei" contains _)

    // 10 chars doesn't look nice! ...
    //s = s take 10 // this'll do for now, the database will ensure
                  // uniqueness? If I use a nosql database, then perhaps
                  // use 15 instead?  (32^10 is huge: 1 million billions!)

    // ... instead take 3 chars only and start and end with a digit, always.
    // Then people'll understand it's an ID? Since it ends with a digit?
    def randomDigit = (java.lang.Math.random() * 10).toInt.toString
    s = randomDigit + s.take(3) + randomDigit

    // It's the responsibility of database not to overwrite anything,
    // but rather fail, and the caller could retry with a new id.
    // 10 * 32 * 32 * 32 * 10 = 3 000 000.

    s
    /*
    // Or use Apache Commons, org.apache.commons.lang.RandomStringUtils:
    RandomStringUtils.random(10 /*count*/, 0 /*start*/, 32 /*end*/,
      false /*letters only*/, false /*numbers only*/,
      // Characters to pick from. Alphanumeric, but all vowels except for "uy"
      // removed. This results in 32 characters, and (since most vowels are
      // removed) virtually no possibility of ugly words, like "yourfatrat".
      "bcdfghjklmnpqrstuvwxyz0123456789", _random)
    */
  }

  // This isn't really a secret salt. A secret salt should be kept secret
  // in the database, fetched via Dao.secretSalt and specified via useSalt().
  // I think the salt better be fairly long, otherwise it'd be possible to
  // find out what is the salt, since you might guess that a salt + email
  // is hashed to the dwCoUserEmailSH cookie, and you have your hash and
  // your email and can thus do a brute force attack.
  private var _hashSalt = "94k2xIf1AoVkbx928_"

  /** Sets the salt used when hashing (no related to the random numbers). */
  def setHashSalt(salt: String) { _hashSalt = salt }

  def saltAndHash(hashLength: Int)(text: String): String = {
    val saltAndText = _hashSalt + text
    hashSha1Base64UrlSafe(saltAndText) take hashLength
  }

  val hashLengthEmail = 20
  val hashLengthIp = 20

  def saltAndHashEmail = saltAndHash(hashLengthEmail) _
  def saltAndHashIp = saltAndHash(hashLengthIp) _

  SECURITY; COULD // use SHA-256 instead.
  private def mdSha1 = js.MessageDigest.getInstance("SHA-1") // not thread safe

  def hashSha1Base64UrlSafe(text: String): String =
    acb.Base64.encodeBase64URLSafeString(mdSha1.digest(text.getBytes("UTF-8")))

  def hideEmailLocalPart(emailAddress: String): String =
    if (emailAddress.isEmpty) ""
    else DotDotDot + emailAddress.dropWhile(_ != '@')

  def isEmailLocalPartHidden(emailAddress: String): Boolean =
    emailAddress startsWith DotDotDot

  private val DotDotDot = "..."


  // ------ Diff, match, patch

  def makePatch(from: String, to: String): String = {
    val dmp = new name.fraser.neil.plaintext.diff_match_patch
    var diffs = dmp.diff_main(from, to)
    dmp.diff_cleanupSemantic(diffs)
    val patches = dmp.patch_make(from, diffs)
    val patchText = dmp.patch_toText(patches)
    patchText
  }

  def applyPatch(patchText: String, to: String): String = {
    val textToPatch = to
    // COULD check [1, 2, 3, …] to find out if the patch applied
    // cleanaly. (The result is in [0].)
    val dmp = new name.fraser.neil.plaintext.diff_match_patch
    type P = name.fraser.neil.plaintext.diff_match_patch.Patch
    val patches: ju.List[P] = dmp.patch_fromText(patchText) // silly API, ...
    val p2 = patches.asInstanceOf[ju.LinkedList[P]] // returns List but needs...
    val result = dmp.patch_apply(p2, textToPatch) // ...a LinkedList
    val newText = result(0).asInstanceOf[String]
    newText
  }


  // ------ Utilities

  // The ternary operator: `if (p) a else b'  <-->  `p ? a | b'
  class IfTrue[A](b: => Boolean, t: => A) { def |(f: => A) = if (b) t else f }
  class MakeIfTrue(b: => Boolean) { def ?[A](t: => A) = new IfTrue[A](b,t) }
  implicit def autoMakeIfTrue(b: => Boolean) = new MakeIfTrue(b)


  /**
   * Pimps `Regex` with `matches(text): Boolean` and `misses(text): Boolean`.
   */
  implicit def regexToRichRegex(r: Regex) = new RichRegex(r)
  class RichRegex(underlying: Regex) {
    def matches(s: String) = underlying.pattern.matcher(s).matches
    def misses(s: String) = !matches(s)
  }

  implicit class RegexToFindGroup(regex: scala.util.matching.Regex) {
    def findGroupIn(text: String): Option[String] =
      text match {
        case regex(firstGroup) =>
          Option(firstGroup)
        case _ =>
          None
      }
  }

  implicit class RichOption[T](underlying: Option[T]) {
    def oneIfDefined: Int = if (underlying.isDefined) 1 else 0
    def is(value: T): Boolean = underlying.contains(value)
    def isNot(value: T): Boolean = !underlying.contains(value)
    def isSomethingButNot(value: T): Boolean = underlying.isDefined && !underlying.contains(value)
  }

  // Doesn't work, causes error: maxOptBy is not a member of Seq[com.debiki.core.Post]
  implicit class RichSeq[T](underlying: scala.collection.Seq[T]) {
    def maxOptBy[B](f: T => B)(implicit cmp: Ordering[B]): Option[T] = {
      if (underlying.isEmpty) None
      else Some(underlying.maxBy(f))
    }
  }
  // ... but this works:
  def maxOptBy[T, B](underlying: scala.collection.Seq[T])(f: T => B)(
          implicit cmp: Ordering[B]): Option[T] = {
      if (underlying.isEmpty) None
      else Some(underlying.maxBy(f))
    }

  implicit class BlankStringToNone(underlying: Option[String]) {
    def noneIfBlank: Option[String] =
      if (underlying.exists(_.trim.isEmpty)) None
      else underlying  // <—— not trimmed

    def trimNoneIfBlank: Option[String] =
      underlying.map(_.trim) match {
        case Some("") => None
        case x => x  // <—— trimmed
      }

    def isEmptyOrContainsBlank: Boolean =
      underlying.isEmpty || underlying.get.trim.isEmpty
  }


  private val AToZUnderscoreRegex = "^[a-zA-Z_]*$".r
  private val VariableNameRegex = "^[a-zA-Z_][a-zA-Z0-9_]*$".r

  /** Checks that all fields names are okay variable names,
    * and that all values are numbers, or also okay variable names.
    * Just to avoid any unexpected things like some kind of injection.
    */
  def anyWeirdJsObjField(obj: JsObject, maxLength: Int): Option[String] = {
    for ((fieldName, fieldValue) <- obj.fields) {
      if (fieldName.isEmpty) return Some("Empty field name")
      if (!fieldName.isOkVariableName) return Some(s"Weird field name: $fieldName")
      if (fieldName.length > maxLength) return Some(s"Too long field name: $fieldName")
      fieldValue match {
        case _: JsNumber =>
          // Fine
        case s: JsString =>
          if (s.value.isEmpty) return Some(s"Empty value for field $fieldName")
          if (!s.value.isOkVariableName) return Some(s"Bad value for field $fieldName: $fieldValue")
          if (s.value.length > maxLength) return Some(s"Too long field value, $fieldName: $fieldValue")
        case _ =>
          return Some(s"Value of field $fieldName is weird")
      }
    }
    None
  }

  /** Default value doesn't work with anyJsonObj.flatMap(anyWeirdJsObjField)
    */
  def anyWeirdJsObjField(obj: JsObject): Option[String] =
    anyWeirdJsObjField(obj, maxLength = 100)


  /**
   * Pimps `String` with `matches(regex): Boolean` and `misses(regex)`
   * and `dropRightWhile(Char => Boolean)` and `takeRightWhile`.
   */
  implicit def stringToRichString(s: String): RichString = new RichString(s)

  class RichString(underlying: String) {

    def matches(regex: Regex): Boolean = regex.pattern.matcher(underlying).matches
    def misses(regex: Regex): Boolean = !matches(regex)

    def dropRightWhile(f: Char => Boolean): String = {
      val keepIx = underlying.lastIndexWhere(!f(_))
      if (keepIx == -1) return ""
      val kept = underlying.dropRight(underlying.length - 1 - keepIx)
      kept
    }

    def takeRightWhile(f: Char => Boolean): String = {
      val dropIx = underlying.lastIndexWhere(!f(_))
      if (dropIx == -1) return underlying
      val kept = underlying.takeRight(underlying.length - 1 - dropIx)
      kept
    }

    def toIntOption: Option[Int] =
      Try(underlying.toInt).toOption

    def toFloatOption: Option[Float] =
      Try(underlying.toFloat).toOption

    def orIfEmpty[A >: String](other: => A): A = {
      if (underlying nonEmpty) underlying
      else other
    }

    def trimNoneIfEmpty: Option[String] = {
      val trimmed = underlying.trim
      if (trimmed.isEmpty) None
      else Some(trimmed)
    }

    def isAToZUnderscoreOnly: Boolean =
      AToZUnderscoreRegex.pattern.matcher(underlying).matches

    def isOkVariableName: Boolean =
      VariableNameRegex.pattern.matcher(underlying).matches
  }

  def charIsAzNumOrUnderscore(c: Char): Boolean =
    charIsAzOrNum(c) || c == '_'

  def charIsAz(c: Char): Boolean =
    (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')

  def charIsAzUnderscore(c: Char): Boolean =
    charIsAz(c) || c == '_'

  def charIsAzOrNum(c: Char): Boolean =
    charIsAz(c) || charIsNum(c)

  def charIsNumOrDotDash(c: Char): Boolean = charIsNum(c) || c == '.' || c == '-'

  def charIsNum(c: Char): Boolean = c >= '0' && c <= '9'

  /**
   * It's impossible to place breakpoints in Specs test suites, so
   * instead I place a breakpoint in this function, and call it from
   * the test suite, on the line where I'd like to have a "breakpoint".
   */
  def debugBreakpointA {
    println("debugBreakpointA")
  }

  def debugBreakpointB {
    println("debugBreakpointA")
  }


  /**
   * Indents arguments that should be indented. For example:
   * {{{
   * t"""
   *   |Hello
   *   |   $name"""
   * }}}
   * would result in e.g.:
   * """
   * Hello
   *    Planet
   *    Earth"""
   *
   * BUT Somewhat BROKEN because this doesn't work:
    scala> val ir = """(.*)""".r
    ir: scala.util.matching.Regex = (.*)

    scala> "aa\n   " match { case ir(a) => a; case y => "noo" }
    res22: String = noo
   */
  implicit class StripIndentStringInterpolator(val stringContext: StringContext) {
    // Find test cases in StringInterpolatorsTest.

    def i(args: Any*): String = {
      stringContext.checkLengths(args)
      val stringBuilder = new StringBuilder()

      for ((partNotStripped, arg) <- stringContext.parts zip args) {
        val part = stripped(partNotStripped)
        stringBuilder append part

        val argIndented = part match {
          case IndentationRegex(indentation) =>
            arg.toString.replaceAll("\n", "\n" + indentation)
          case _ =>
            arg.toString
        }

        stringBuilder append argIndented
      }

      if (stringContext.parts.size > args.size)
        stringBuilder append stripped(stringContext.parts.last)

      stringBuilder.toString
    }

    private def stripped(string: String): String =
      StripRegex.replaceAllIn(string, "\n")

    private val StripRegex = """\n\s*\|""".r
    private val IndentationRegex = """\n(\s+)$""".r
  }


  implicit class OneLineStringInterpolator(val stringContext: StringContext) {
    // Find test cases in StringInterpolatorsTest.

    // One line: everything on one line, newlines converted to spaces.
    def o(args: Any*): String = {
      stringContext.checkLengths(args)
      val stringBuilder = new StringBuilder()
      stringBuilder append withSpacesCollapsed(trimLeft(stringContext.parts.head))
      for ((part, arg) <- stringContext.parts.tail zip args) {
        stringBuilder append arg.toString
        stringBuilder append withSpacesCollapsed(part)
      }
      stringBuilder.toString
    }

    private def trimLeft(string: String): String =
      TrimLeftRegex.replaceAllIn(string, "")

    private def withSpacesCollapsed(string: String): String =
      CollapseSpacesRegex.replaceAllIn(string, " ")

    private val TrimLeftRegex = """^\s*""".r
  }

  val CollapseSpacesRegex: Regex = """\s\s*""".r


  implicit class RichLinkedHashMap[A, B](val underlying: mutable.LinkedHashMap[A, B])
      extends AnyVal {

    def removeWhile(predicate: ((A, B)) => Boolean) {
      val keysToRemove = underlying.iterator.takeWhile(predicate).map(_._1)
      keysToRemove.foreach(underlying.remove)
    }

    def removeWhileValue(predicate: (B) => Boolean) {
      val keysToRemove = underlying.iterator.takeWhile(entry => predicate(entry._2)).map(_._1)
      keysToRemove.foreach(underlying.remove)
    }

    def removeWhileKey(predicate: (A) => Boolean) {
      val keysToRemove = underlying.keysIterator.takeWhile(predicate)
      keysToRemove.foreach(underlying.remove)
    }
  }

}


// vim: fdm=marker et ts=2 sw=2 fo=tcqwn list

