// Copyright (c) 2021 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.script.export

import com.daml.lf.language.Ast
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class EncodeTypeSpec extends AnyFreeSpec with Matchers {
  import Encode._
  import AstSyntax._

  "encodeType" - {
    "builtin types" - {
      val builtins: Seq[(Ast.BuiltinType, String)] = Seq(
        Ast.BTInt64 -> "Int",
        Ast.BTNumeric -> "Numeric",
        Ast.BTText -> "Text",
        Ast.BTTimestamp -> "Time",
        Ast.BTParty -> "Party",
        Ast.BTUnit -> "()",
        Ast.BTBool -> "Bool",
        Ast.BTList -> "[]",
        Ast.BTOptional -> "Optional",
        Ast.BTTextMap -> "DA.TextMap.TextMap",
        Ast.BTGenMap -> "DA.Map.Map",
        Ast.BTUpdate -> "Update",
        Ast.BTScenario -> "Scenario",
        Ast.BTDate -> "Date",
        Ast.BTContractId -> "ContractId",
        Ast.BTArrow -> "(->)",
        Ast.BTAny -> "Any",
        Ast.BTTypeRep -> "TypeRep",
        Ast.BTAnyException -> "AnyException",
        Ast.BTRoundingMode -> "RoundingMode",
        Ast.BTBigNumeric -> "BigNumeric",
      )
      builtins foreach { case (ty, repr) =>
        s"$ty" in {
          encodeType(Ast.TBuiltin(ty)).render(80) shouldBe repr
        }
      }
    }
    "simple types" - {
      "type variable" in {
        encodeType(vFoo).render(80) shouldBe "foo"
      }
      "natural number" in {
        encodeType(Ast.TNat.values(2)).render(80) shouldBe "2"
      }
      "type constructor" in {
        encodeType(Ast.TTyCon(nFoo)).render(80) shouldBe "Module.Foo"
      }
    }
    "composed types" - {
      "type synonym application" in {
        val ty = synApp(nFoo, int, synApp(nBar, text))
        encodeType(ty).render(80) shouldBe "Module.Foo Int (Module.Bar Text)"
      }
      "type application" in {
        val ty = vFoo :@ int :@ (vBar :@ text)
        encodeType(ty).render(80) shouldBe "foo Int (bar Text)"
      }
      "tuple type" in {
        val ty = tuple(int, text, vFoo :@ int)
        encodeType(ty).render(80) shouldBe "(Int, Text, foo Int)"
      }
      "list type" in {
        val ty = list(int)
        encodeType(ty).render(80) shouldBe "[Int]"
      }
      "arrow type" in {
        val ty = (vFoo :@ int =>: text) =>: int =>: text
        encodeType(ty).render(80) shouldBe "(foo Int -> Text) -> Int -> Text"
      }
    }
  }
}
