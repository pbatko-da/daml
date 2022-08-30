package com.daml.platform.apiserver.update

import com.daml.error.ErrorsAssertions
import org.scalatest.{EitherValues, OptionValues}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class UpdateMaskTrieSpec
    extends AnyFreeSpec
    with Matchers
    with EitherValues
    with OptionValues
    with ErrorsAssertions {

  "test getting subtrees" - {
    val allPaths = Seq(
      Seq("a1", "a2", "c3"),
      Seq("a1", "b2", "c3"),
      Seq("a1", "b2", "d3", "a4"),
      Seq("a1", "b2", "d3", "b4"),
      Seq("b1"),
    )
    val tree = UpdatePathsTrie.fromPaths(allPaths)
    "whole tree" in {
      tree.toPaths shouldBe allPaths
    }
    "proper subtrees" in {
      tree.subtree(Seq("a1")).value.toPaths shouldBe Seq(
        Seq("a2", "c3"),
        Seq("b2", "c3"),
        Seq("b2", "d3", "a4"),
        Seq("b2", "d3", "b4"),
      )
      tree.subtree(Seq.empty).value.toPaths shouldBe allPaths
      tree.subtree(Seq("a1", "b2")).value.toPaths shouldBe Seq(
        Seq("c3"),
        Seq("d3", "a4"),
        Seq("d3", "b4"),
      )
    }
    "non-existing subtrees" in {
      tree.subtree(Seq("a1", "b2", "dummy")) shouldBe None
      tree.subtree(Seq("dummy")) shouldBe None
      tree.subtree(Seq("")) shouldBe None
    }
    "existing but empty subtrees" in {
      tree.subtree(Seq("b1")).value.toPaths shouldBe Seq.empty
      tree.subtree(Seq("a1", "b2", "c3")).value.toPaths shouldBe Seq.empty
      tree.subtree(Seq("a1", "b2", "d3", "b4")).value.toPaths shouldBe Seq.empty
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
        Seq(Seq("foo"))
      )
      trie0 shouldBe UpdatePathsTrie(
        nodes = List(
          "foo" -> UpdatePathsTrie(
            nodes = List.empty,
            exists = true,
          )
        ),
        exists = false,
      )
    }
    "one multiple element path tree" in {
      val trie2 = UpdatePathsTrie.fromPaths(
        Seq(Seq("foo", "bar", "baz"))
      )
      trie2 shouldBe UpdatePathsTrie(
        nodes = List(
          "foo" -> UpdatePathsTrie(
            nodes = List(
              "bar" -> UpdatePathsTrie(
                nodes = List(
                  "baz" -> UpdatePathsTrie(nodes = List.empty, exists = true)
                ),
                exists = false,
              )
            ),
            exists = false,
          )
        ),
        exists = false,
      )
    }
    "three paths tree" in {
      val trie2 = UpdatePathsTrie.fromPaths(
        Seq(
          Seq("foo", "bar", "baz"),
          Seq("foo", "bar"),
          Seq("foo", "alice"),
          Seq("bob", "eve"),

        )
      )
      trie2 shouldBe UpdatePathsTrie(
        false,
        "foo" -> UpdatePathsTrie(
          false,
          "bar" -> UpdatePathsTrie(
            true,
            "baz" -> UpdatePathsTrie(true)
          ),
          "alice" -> UpdatePathsTrie(true)
        ),
        "bob" -> UpdatePathsTrie(
          false,
          "eve" -> UpdatePathsTrie(true)
        )
      )
    }

  }

}
