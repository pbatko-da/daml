// Copyright (c) 2022 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.platform.apiserver.update

import com.daml.platform.apiserver.update.UpdatePathsTrie.newEmpty

import collection.mutable
import scala.annotation.tailrec

sealed trait UpdatePathModifier {
  def toRawString: String
}
object UpdatePathModifier {
  object Merge extends UpdatePathModifier {
    override def toRawString: String = "!merge"
  }
  object Replace extends UpdatePathModifier {
    override def toRawString: String = "!replace"
  }
  object NoModifier extends UpdatePathModifier {
    override def toRawString: String = ""
  }
}

//case class ExistingPathAttrs(
//    modifier: UpdatePathModifier
////    defaultValue: Any,
//)

case class MatchResult(
    isExact: Boolean,
    matchedPath: ParsedUpdatePath
) {
  def updatePathModifier: UpdatePathModifier = matchedPath.modifier
}

// TODO pbatko: Clean-up impl

/** @param modifierO - non empty to signify a path ending in this node exists
  */
case class UpdatePathsTrie(
    nodes: mutable.SortedMap[String, UpdatePathsTrie],
    var modifierO: Option[UpdatePathModifier],
) {

  def findMatch(updatePath: List[String]): Option[MatchResult] = {
    val subtree = getSubtree(updatePath)
    subtree.modifierO match {
      case Some(modifier) => Some(MatchResult(isExact = true, matchedPath = ParsedUpdatePath(updatePath, modifier)))
      case None =>
        val properPrefixesLongestFirst =
          updatePath.inits.filter(init => init.size != updatePath.size).toList.sortBy(-_.length)
        properPrefixesLongestFirst.iterator
          .map(prefix => getSubtree(prefix).modifierO.map(_ -> prefix))
          .collectFirst { case Some((modifier, prefix)) =>
            MatchResult(isExact = false, matchedPath = ParsedUpdatePath(prefix, modifier))
          }
    }
  }

  def isEmpty: Boolean = nodes.isEmpty
//  def exists: Boolean = modifierO.nonEmpty

  @tailrec
  final def insert(path: List[String], modifier: UpdatePathModifier): Unit = {
    path match {
      case Nil =>
        this.modifierO = Some(modifier)
      case key :: subpath =>
        if (!nodes.contains(key)) {
          nodes.put(key, UpdatePathsTrie.newEmpty)
        }
        nodes(key).insert(subpath, modifier)
    }
  }

  def getSubtree(path: List[String]): UpdatePathsTrie = {
    path match {
      case Nil => this
      case head :: subpath =>
        nodes.get(head).map(_.getSubtree(subpath)).getOrElse(newEmpty)
    }
  }

//  def calculateUpdate[A](
//      updatePath: List[String]
//  )(newValueCandidate: A, defaultValue: A): Option[A] = {
//    val updateO: Option[A] = subtreeO(updatePath)
//      .filter(_.exists)
//      // TODO pbatko: Compare with default value and return either Merge or Replace, or default ?
//      .map(_ => newValueCandidate)
//    updateO.orElse {
//      val properPrefixes = updatePath.inits.filter(init => init.size != updatePath.size)
//      val isProperPrefixMatch = properPrefixes.exists(subtreeO(_).fold(false)(_.exists))
//      Option.when(isProperPrefixMatch && newValueCandidate != defaultValue)(newValueCandidate)
//    }
//  }

  def toPaths: Seq[ParsedUpdatePath] = {
    def iter(path: List[String], trie: UpdatePathsTrie): Seq[ParsedUpdatePath] = {
      if (trie.isEmpty) {
        // TODO pbatko
        require(trie.modifierO.isDefined)
        Seq(ParsedUpdatePath(path, trie.modifierO.get))
      } else {
        val ans = for {
          (key, subnode) <- trie.nodes.iterator
          newPath = path :+ key
          fullPath <- iter(newPath, subnode)
        } yield fullPath
        val x = ans.toSeq
        x
      }
    }
    if (isEmpty) {
      Seq.empty
    } else
      iter(path = List.empty, trie = this)
  }

}

object UpdatePathsTrie {

  def apply(
      modifierO: Option[UpdatePathModifier],
      nodes: (String, UpdatePathsTrie)*
  ) = new UpdatePathsTrie(
    nodes = mutable.SortedMap.from(nodes),
    modifierO = modifierO,
  )

  // TODO pbatko: Review usage, remove
//  def apply(
//      exists: Boolean,
//      nodes: List[(String, UpdatePathsTrie)],
//  ) = new UpdatePathsTrie(
//    nodes = mutable.SortedMap.from(nodes),
//    exists = exists,
//  )
//
//  def apply(
//      exists: Boolean,
//      nodes: mutable.SortedMap[String, UpdatePathsTrie],
//  ) = new UpdatePathsTrie(
//    nodes = nodes,
//    exists = exists,
//  )

  def newEmpty: UpdatePathsTrie =
    new UpdatePathsTrie(
      nodes = collection.mutable.SortedMap.empty,
      modifierO = None,
    )

  def fromPaths(paths: Seq[ParsedUpdatePath]): UpdatePathsTrie = {
    val trie = newEmpty
    if (paths.nonEmpty) {
      paths.foreach(parsedPath =>
        trie.insert(
          path = parsedPath.path,
          modifier = parsedPath.modifier,
        )
      )
    }
    trie
  }
}
