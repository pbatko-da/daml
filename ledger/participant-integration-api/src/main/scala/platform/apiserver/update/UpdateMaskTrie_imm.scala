// Copyright (c) 2022 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.platform.apiserver.update

//case class UpdateMaskTrie_imm(
//    nodes: collection.immutable.SortedMap[String, UpdateMaskTrie_imm],
//    exists: Boolean,
//)

//// TODO pbatko: Use mutable trie as an internal builder for immutable trie.
//object UpdateMaskTrie_imm {
//
//  def fromPaths(paths: Seq[Seq[String]]): UpdateMaskTrie_imm = {
//    val mut = UpdatePathsTrie.fromPaths(paths)
//    fromMutable(mut)
//  }
//
//  private def fromMutable(mut: UpdatePathsTrie): UpdateMaskTrie_imm = {
//    new UpdateMaskTrie_imm(
//      nodes = collection.immutable.SortedMap.from(mut.nodes.toMap.view.mapValues(fromMutable)),
//      exists = mut.exists,
//    )
//  }
//}
