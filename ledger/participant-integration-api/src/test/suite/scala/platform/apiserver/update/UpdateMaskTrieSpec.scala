// Copyright (c) 2022 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.platform.apiserver.update

import com.daml.error.ErrorsAssertions
import org.scalatest.{EitherValues, OptionValues}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import com.daml.platform.apiserver.update.UpdatePathModifier._

class UpdateMaskTrieSpec
    extends AnyFreeSpec
    with Matchers
    with EitherValues
    with OptionValues
    with ErrorsAssertions {

  "test getting subtrees" - {
    val allPaths = Seq(
      "a1.a2.c3",
      "a1.b2.c3",
      "a1.b2.d3.a4",
      "a1.b2.d3.b4",
      "b1",
    ).map(ParsedUpdatePath.parse)
    val tree = UpdatePathsTrie.fromPaths(allPaths)
    "whole tree" in {
      tree.toPaths shouldBe allPaths
    }
    "proper subtrees" in {
      tree.getSubtree(List("a1")).toPaths shouldBe Seq(
        "a2.c3",
        "b2.c3",
        "b2.d3.a4",
        "b2.d3.b4",
      ).map(ParsedUpdatePath.parse)
      tree.getSubtree(List.empty).toPaths shouldBe allPaths
      tree.getSubtree(List("a1", "b2")).toPaths shouldBe Seq(
        "c3",
        "d3.a4",
        "d3.b4",
      ).map(ParsedUpdatePath.parse)
    }
    "non-existing subtrees" in {
      tree.getSubtree(List("a1", "b2", "dummy")) shouldBe None
      tree.getSubtree(List("dummy")) shouldBe None
      tree.getSubtree(List("")) shouldBe None
    }
    "existing but empty subtrees" in {
      tree.getSubtree(List("b1")).toPaths shouldBe Seq.empty
      tree.getSubtree(List("a1", "b2", "c3")).toPaths shouldBe Seq.empty
      tree.getSubtree(List("a1", "b2", "d3", "b4")).toPaths shouldBe Seq.empty
    }
  }

//  "resetting nested message in shortcut notation" in {
//    // Do reset "foo.bar" to its default value (None) when it's exact path is specified
//    val trie = UpdateMaskTrie_mut.fromPaths(Seq(Seq("foo", "bar")))
//    trie.determineUpdate(
//      updatePath = Seq("foo", "bar")
//    )(
//      newValueCandidate = None,
//      defaultValue = None,
//    ) shouldBe Some(None)
//
//    // Do not reset "foo.bar" to it's default value (None) when it's exact path is not specified - case 1: a longer path is specified
//    val trie2 = UpdateMaskTrie_mut.fromPaths(
//      Seq(Seq("foo", "bar", "baz"))
//    )
//    trie2.determineUpdate(
//      updatePath = Seq("foo", "bar")
//    )(
//      newValueCandidate = None,
//      defaultValue = None,
//    ) shouldBe None
//
//    // Do not reset "foo.bar" to it's default value (None) when it's exact path is not specified - case 1: a shorter path is specified
//    val trie3 = UpdateMaskTrie_mut.fromPaths(
//      Seq(Seq("foo"))
//    )
//    trie3.determineUpdate(
//      updatePath = Seq("foo", "bar")
//    )(
//      newValueCandidate = None,
//      defaultValue = None,
//    ) shouldBe None
//
//    // Do update "foo.bar" to a non default value when it's exact path is specified
//    val trie4 = UpdateMaskTrie_mut.fromPaths(
//      Seq(Seq("foo", "bar"))
//    )
//    trie4.determineUpdate(
//      updatePath = Seq("foo", "bar")
//    )(
//      newValueCandidate = Some("nonDefaultValue"),
//      defaultValue = None,
//    ) shouldBe Some(Some("nonDefaultValue"))
//
//    // Do not update "foo.bar" to a non default value when it's exact path is not specified - case 1: a longer path is specified
//    val trie5 = UpdateMaskTrie_mut.fromPaths(
//      Seq(Seq("foo", "bar", "baz"))
//    )
//    trie5.determineUpdate(
//      updatePath = Seq("foo", "bar")
//    )(
//      newValueCandidate = Some("nonDefaultValue"),
//      defaultValue = None,
//    ) shouldBe None
//
//    // Do update "foo.bar" to a non default value when it's exact path is not specified - case 1: a shorter path is specified
//    val trie6 = UpdateMaskTrie_mut.fromPaths(
//      Seq(Seq("foo"))
//    )
//    trie6.determineUpdate(
//      updatePath = Seq("foo", "bar")
//    )(
//      newValueCandidate = Some("nonDefaultValue"),
//      defaultValue = None,
//    ) shouldBe Some(Some("nonDefaultValue"))
//  }

  "construct correct trie" - {
    "one single element path tree" in {
      val trie0 = UpdatePathsTrie.fromPaths(
        Seq("foo").map(ParsedUpdatePath.parse)
      )
      trie0 shouldBe UpdatePathsTrie(
        None,
        "foo" -> UpdatePathsTrie(Some(NoModifier)),
      )
    }
    "one multiple element path tree" in {
      val trie2 = UpdatePathsTrie.fromPaths(
        Seq("foo.bar.baz").map(ParsedUpdatePath.parse)
      )
      trie2 shouldBe UpdatePathsTrie(
        None,
        "foo" -> UpdatePathsTrie(
          None,
          "bar" -> UpdatePathsTrie(
            None,
            "baz" -> UpdatePathsTrie(
              Some(NoModifier)
            ), // TODO pbatko: Test non-default update modifiers
          ),
        ),
      )
    }
    "three paths tree" in {
      val trie2 = UpdatePathsTrie.fromPaths(
        Seq(
          "foo.bar.baz",
          "foo.bar",
          "foo.alice",
          "bob.eve",
        ).map(ParsedUpdatePath.parse)
      )
      trie2 shouldBe UpdatePathsTrie(
        None,
        "foo" -> UpdatePathsTrie(
          None,
          "bar" -> UpdatePathsTrie(
            Some(NoModifier),
            "baz" -> UpdatePathsTrie(Some(NoModifier)),
          ),
          "alice" -> UpdatePathsTrie(Some(NoModifier)),
        ),
        "bob" -> UpdatePathsTrie(
          None,
          "eve" -> UpdatePathsTrie(Some(NoModifier)),
        ),
      )
    }

  }
  // TODO pbatko: Validate tree by checking if it is a subtree of a maximum tree
  // TODO pbatko: Verifying that modifiers are only on annotations paths is not trie's responsibility. Should be performed earlier.
  // TODO pbatko: Duplicate update paths: 'a.b!merge', 'a.b!replace', 'a.b'.

}
