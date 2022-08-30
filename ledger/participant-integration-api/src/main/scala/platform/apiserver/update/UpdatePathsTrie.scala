// Copyright (c) 2022 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.platform.apiserver.update

import collection.mutable
sealed trait UpdatePathModifier
object UpdatePathModifier {
  object Merge extends UpdatePathModifier
  object Replace extends UpdatePathModifier
  object Default extends UpdatePathModifier
}

case class UpdatePath(path: Seq[String], modifier: UpdatePathModifier) {}

// TODO pbatko: Clean-up impl
/** @param exists set if there is a path ending in this node
  */
case class UpdatePathsTrie(
    val nodes: mutable.SortedMap[String, UpdatePathsTrie],
    var exists: Boolean,
) {

  def isEmpty = nodes.isEmpty

  final def insert(path: Seq[String]): Unit = {
    path.toList match {
      case Nil =>
        this.exists = true
      case key :: subpath =>
        if (!nodes.contains(key)) {
          nodes.put(key, UpdatePathsTrie.newEmpty)
        }
        nodes(key).insert(subpath)
    }
  }

  def subtree(path: Seq[String]): Option[UpdatePathsTrie] = {
    path.toList match {
      case Nil => Some(this)
      case head :: subpath =>
        nodes.get(head).flatMap(_.subtree(subpath))
    }
  }

  def determineUpdate[A](
      updatePath: Seq[String]
  )(newValueCandidate: A, defaultValue: A): Option[A] = {
    subtree(updatePath)
      .filter(_.exists)
      .map(_ => newValueCandidate)
      .orElse {
        val properPrefixes = updatePath.inits.filter(init => init.size != updatePath.size)
        val isProperPrefixMatch = properPrefixes.exists(subtree(_).fold(false)(_.exists))
        Option.when(isProperPrefixMatch && newValueCandidate != defaultValue)(newValueCandidate)
      }
  }

  def toPaths: Seq[Seq[String]] = {
    def iter(path: Seq[String], trie: UpdatePathsTrie): Seq[Seq[String]] = {
      if (trie.isEmpty) {
        Seq(path)
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
      iter(path = Seq.empty, trie = this)
  }

}

object UpdatePathsTrie {

  def apply(
             exists: Boolean,
             nodes: (String, UpdatePathsTrie)*,
           ) = new UpdatePathsTrie(
    nodes = mutable.SortedMap.from(nodes),
    exists = exists,
  )

  def apply(
             exists: Boolean,
             nodes: List[(String, UpdatePathsTrie)],

           ) = new UpdatePathsTrie(
    nodes = mutable.SortedMap.from(nodes),
    exists = exists,
  )

  def apply(
             exists: Boolean,
      nodes: mutable.SortedMap[String, UpdatePathsTrie],

  ) = new UpdatePathsTrie(
    nodes = nodes,
    exists = exists,
  )

  def newEmpty: UpdatePathsTrie =
    new UpdatePathsTrie(
      nodes = collection.mutable.SortedMap.empty,
      exists = false,
    )

  def fromPaths(paths: Seq[Seq[String]]): UpdatePathsTrie = {
    val trie = newEmpty
    if (paths.nonEmpty) {
      paths.foreach(path =>
        trie.insert(
          path = path
        )
      )
    }
    trie
  }
}
