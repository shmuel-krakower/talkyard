/**
 * Copyright (c) 2020 Kaj Magnus Lindberg
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

package talkyard.server.sitepatch

import com.debiki.core._
import com.debiki.core.Prelude._
import debiki.EdHttp.ResultException
import debiki.TextAndHtmlMaker
import debiki.dao._
import org.scalatest._
import scala.collection.immutable


trait TwoPeopleChatSpecTrait {
  self: SitePatcherAppSpec =>


  def makeTwoPeopleChatTests()  {
    lazy val siteDao = globals.siteDao(site.id)

    lazy val (site, forum, oldPageId, oldPagePosts, owen, merrylMember, dao) =
      createSiteWithOneCatPageMember("2-ppl-chat", pageExtId = None)

    lazy val chatPagePatch = SimplePagePatch(
      extId = "chatPageExtId",
      pageType = Some(PageType.PrivateChat),
      categoryRef = Some(s"tyid:${forum.defaultCategoryId}"),
      authorRef = Some(s"tyid:$SysbotUserId"),
      pageMemberRefs = Vector(alice.extIdAsRef.get, bob.extIdAsRef.get),
      title = "Chat Page Title",
      body = "Chat between Alice and Bob")

    lazy val aliceSaysHiBobMessage = SimplePostPatch(
      extId = "aliceSaysHiBobMessage-ext-id",
      postType = PostType.ChatMessage,
      pageRef = ParsedRef.ExternalId(chatPagePatch.extId),
      parentNr = None,
      authorRef = alice.extIdAsRef.get,
      body = "Hi Bob, Alice here")

    lazy val bobSaysHiAliceMessage = SimplePostPatch(
      extId = "bobSaysHiAliceMessage-ext-id",
      postType = PostType.ChatMessage,
      pageRef = ParsedRef.ExternalId(chatPagePatch.extId),
      parentNr = None,
      authorRef = bob.extIdAsRef.get,
      body = "Bob is my name. I am Bob. How did you know? Was it mind reading? " +
        "But I wasn't thinking about my name when you said hi")

    lazy val alice = createPasswordUserGetDetails("alice_un", dao, extId = Some("Alice-ExtId"))
    lazy val bob = createPasswordUserGetDetails("bob_un", dao, extId = Some("Bob-ExtId"))
    lazy val sarah = createPasswordUserGetDetails("sarah_un", dao, extId = Some("Sarah-ExtId"))
    lazy val sanjo = createPasswordUserGetDetails("sanjo_un", dao, extId = Some("Sanjo-ExtId"))

    "Create anew site with a chat topic" in {
      site
    }

    "Upsert a chat topic with a single message from Alice to Bob" in {
      val simplePatch = SimpleSitePatch(
        pagePatches = Vector(chatPagePatch),
        postPatches = Vector(aliceSaysHiBobMessage))
      val completePatch = simplePatch.makeComplete(siteDao).getOrDie("TyE7WKRD036")
      upsert(site.id, completePatch)
    }

    "Bob replies" in {
      val simplePatch = SimpleSitePatch(
        postPatches = Vector(bobSaysHiAliceMessage))
      val completePatch = simplePatch.makeComplete(siteDao).getOrDie("TyE7WKRD036")
      upsert(site.id, completePatch)
    }
  }

}
