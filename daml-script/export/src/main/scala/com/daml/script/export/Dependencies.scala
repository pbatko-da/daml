// Copyright (c) 2021 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.script.export

import java.io.FileOutputStream
import java.nio.file.Path

import com.daml.daml_lf_dev.DamlLf
import com.daml.ledger.client.LedgerClient
import com.daml.lf.archive
import com.daml.lf.data.Ref
import com.daml.lf.data.Ref.PackageId
import com.daml.lf.language.{Ast, LanguageVersion}
import com.google.protobuf.ByteString

import scala.concurrent.{ExecutionContext, Future}

object Dependencies {

  // Given a list of root package ids, download all packages transitively referenced by those roots.
  def fetchPackages(client: LedgerClient, references: List[PackageId])(implicit
      ec: ExecutionContext
  ): Future[Map[PackageId, (ByteString, Ast.Package)]] = {
    def go(
        todo: List[PackageId],
        acc: Map[PackageId, (ByteString, Ast.Package)],
    ): Future[Map[PackageId, (ByteString, Ast.Package)]] =
      todo match {
        case Nil => Future.successful(acc)
        case p :: todo if acc.contains(p) => go(todo, acc)
        case p :: todo =>
          client.packageClient.getPackage(p).flatMap { pkgResp =>
            val pkgId = PackageId.assertFromString(pkgResp.hash)
            val pkg =
              archive.archivePayloadDecoder(pkgId).assertFromByteString(pkgResp.archivePayload)._2
            go(todo ++ pkg.directDeps, acc + (pkgId -> ((pkgResp.archivePayload, pkg))))
          }
      }
    go(references, Map.empty)
  }

  /** The Daml-LF version to target based on the DALF dependencies.
    *
    * Chooses the latest LF version among the DALFs but at least 1.7 as that is the minimum required for Daml Script.
    * Returns None if no DALFs are given.
    */
  def targetLfVersion(dalfs: Iterable[LanguageVersion]): Option[LanguageVersion] = {
    if (dalfs.isEmpty) { None }
    else { Some((List(LanguageVersion.v1_7) ++ dalfs).max) }
  }

  def targetFlag(v: LanguageVersion): String =
    s"--target=${v.pretty}"

  def writeDalf(
      file: Path,
      pkgId: PackageId,
      bs: ByteString,
  ): Unit = {
    val os = new FileOutputStream(file.toFile)
    try {
      encodeDalf(pkgId, bs).writeTo(os)
    } finally {
      os.close()
    }
  }

  private def encodeDalf(pkgId: PackageId, bs: ByteString) =
    DamlLf.Archive
      .newBuilder()
      .setHash(pkgId)
      .setHashFunction(DamlLf.HashFunction.SHA256)
      .setPayload(bs)
      .build

  private val providedLibraries: Set[Ref.PackageName] =
    Set("daml-stdlib", "daml-prim", "daml-script").map(Ref.PackageName.assertFromString(_))

  private def isProvidedLibrary(pkgId: PackageId, pkg: Ast.Package): Boolean = {
    pkg.metadata.exists(m =>
      providedLibraries.contains(m.name)
    ) || com.daml.lf.language.StablePackages.Ids.contains(pkgId)
  }

  // Return the package-id appropriate for the --package flag if the package is not builtin.
  def toPackages(
      mainId: PackageId,
      pkgs: Map[PackageId, (ByteString, Ast.Package)],
  ): Option[String] = {
    for {
      main <- pkgs.get(mainId) if !isProvidedLibrary(mainId, main._2)
      pkg = main._2.metadata.map(md => s"${md.name}-${md.version}").getOrElse(mainId.toString)
    } yield pkg
  }
}
