// Copyright (c) 2022 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.ledger.participant.state.index.v2

// TODO pbatko: Clean-up impl
/** @param exists set if there is a path ending in this node
  */
case class UpdateMaskTrie_mut(
    val nodes: collection.mutable.SortedMap[String, UpdateMaskTrie_mut],
    var exists: Boolean,
) {

  def isEmpty = nodes.isEmpty

  final def insert(path: Seq[String]): Unit = {
    path.toList match {
      case Nil =>
        this.exists = true
      case key :: subpath =>
        if (!nodes.contains(key)) {
          nodes.put(key, UpdateMaskTrie_mut.newEmpty)
        }
        nodes(key).insert(subpath)
    }
  }

  def subtree(path: Seq[String]): Option[UpdateMaskTrie_mut] = {
    path.toList match {
      case Nil => Some(this)
      case head :: subpath =>
        nodes.get(head).flatMap(_.subtree(subpath))
    }
  }

  /** @return Some(newValue), indicating an update operation should be performed, if either:
    *          - one of the known paths is equal to the update path,
    *          - or one of the known paths is a prefix of the update path and new value candidate is different that the default value,
    *         None, indicating no update should be performed, otherwise.
    */
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
    def iter(path: Seq[String], trie: UpdateMaskTrie_mut): Seq[Seq[String]] = {
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

object UpdateMaskTrie_mut {
  private def newEmpty: UpdateMaskTrie_mut =
    new UpdateMaskTrie_mut(nodes = collection.mutable.SortedMap.empty, exists = false)

  def fromPaths(paths: Seq[Seq[String]]): UpdateMaskTrie_mut = {
    val trie = newEmpty
    if (paths.nonEmpty) {
      paths.foreach(trie.insert)
    }
    trie
  }
}

case class UpdateMaskTrie_imm(
    nodes: collection.immutable.SortedMap[String, UpdateMaskTrie_imm],
    exists: Boolean,
)

// TODO pbatko: Use mutable trie as an internal builder for immutable trie.
object UpdateMaskTrie_imm {

  def fromPaths(paths: Seq[Seq[String]]): UpdateMaskTrie_imm = {
    val mut = UpdateMaskTrie_mut.fromPaths(paths)
    fromMutable(mut)
  }

  private def fromMutable(mut: UpdateMaskTrie_mut): UpdateMaskTrie_imm = {
    new UpdateMaskTrie_imm(
      nodes = collection.immutable.SortedMap.from(mut.nodes.toMap.view.mapValues(fromMutable)),
      exists = mut.exists,
    )
  }
}
