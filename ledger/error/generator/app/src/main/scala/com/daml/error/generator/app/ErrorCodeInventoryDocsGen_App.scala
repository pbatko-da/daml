package com.daml.error.generator.app

import java.nio.file.{Files, Paths, StandardOpenOption}

import com.daml.error.{ErrorGroupPath, ErrorGroupSegment}
import com.daml.error.generator.{ErrorCodeDocumentationGenerator, ErrorDocItem, GroupDocItem}

import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

object ErrorCodeInventoryDocsGen_App {

  def main(args: Array[String]): Unit = {
    val text = getSelfServiceErrorCodeInventory_asSubsections()

    if (args.length >= 1) {
      val outputFile = Paths.get(args(0))
      val _ = Files.write(outputFile, text.getBytes, StandardOpenOption.CREATE_NEW)
    } else {
      println(text)
    }

  }


  private def getSelfServiceErrorCodeInventory_asSubsections(): String = {
    val items: (Seq[ErrorDocItem], Seq[GroupDocItem]) = new ErrorCodeDocumentationGenerator().getDocItems


    items._1.map(_.code).sorted.foreach(println)

    case class ErrorCode(
                          fullClassName: String,
                          category: String,
                          errorGroupPath: ErrorGroupPath,
                          conveyance: String,
                          code: String,
                          deprecationO: Option[String],
                          explanation: String,
                          resolution: String,
                        )

    val groupSegmentsToExplanationMap: Map[List[ErrorGroupSegment], Option[String]] = items._2.map { groupDocItem: GroupDocItem =>
      groupDocItem.errorGroupPath.segments -> groupDocItem.explanation.map(_.explanation)
    }.toMap

    val errorCodes: Seq[ErrorCode] = items._1.map { (errorDocItem: ErrorDocItem) =>
      ErrorCode(
        fullClassName = errorDocItem.fullClassName,
        category = errorDocItem.category,
        errorGroupPath = errorDocItem.errorGroupPath,
        //        innerMostGroupExplanationO = groupClassNameToGroupExplanation(errorDocItem.errorGroupPath.groupings.last.fullClassName)
        //          .map(_.replace('\n', ' ')),
        conveyance = errorDocItem.conveyance.getOrElse("").replace('\n', ' '),
        code = errorDocItem.code,
        deprecationO = errorDocItem.deprecation.map(_.deprecation.replace('\n', ' ')),
        explanation = errorDocItem.explanation.fold("")(_.explanation).replace('\n', ' '),
        resolution = errorDocItem.resolution.fold("")(_.resolution).replace('\n', ' '),
      )
    }

    class ErrorGroupTree(val name: String,
                         val explanation: Option[String] = None,
                         children: mutable.Map[ErrorGroupSegment, ErrorGroupTree] = new mutable.HashMap[ErrorGroupSegment, ErrorGroupTree](),
                         errorCodes: mutable.Map[String, ErrorCode] = new mutable.HashMap[String, ErrorCode](),
                        ) {

      def sortedSubGroups(): List[ErrorGroupTree] = {
        children.values.toList.sortBy(_.name)
      }

      def sortedErrorCodes(): List[ErrorCode] = {
        errorCodes.values.toList.sortBy(_.code)
      }

      def insertErrorCode(errorCode: ErrorCode): Unit = {
        insert(
          remaining = errorCode.errorGroupPath.segments,
          walkedPath = Nil,
          errorCode = errorCode,
        )
      }

      private def insert(remaining: List[ErrorGroupSegment],
                         errorCode: ErrorCode,
                         walkedPath: List[ErrorGroupSegment]): Unit = {

        remaining match {
          case Nil =>
            assert(!errorCodes.contains(errorCode.code), s"Code: ${errorCode.code} is already present!")
            errorCodes.put(errorCode.code, errorCode)
          case headGroup :: tail =>
            val newWalkedPath = walkedPath :+ headGroup
            if (!children.contains(headGroup)) {
              children.put(
                headGroup,
                new ErrorGroupTree(
                  name = headGroup.docName,
                  explanation = groupSegmentsToExplanationMap(newWalkedPath),
                )
              )
            }
            children(headGroup).insert(remaining = tail, errorCode = errorCode, walkedPath = newWalkedPath)
        }
      }


    }

    object ErrorGroupTree {
      def empty(): ErrorGroupTree = new ErrorGroupTree(
        name = "",
        explanation = None,
      )
    }

    val root = ErrorGroupTree.empty()
    errorCodes.foreach( errorCode => root.insertErrorCode(errorCode))

//    val grouped: Seq[(List[String], Seq[ErrorCode])] = errorCodes
//      .groupBy(_.errorGroupPath)
//      .view
//      .mapValues((errorCodes: Seq[ErrorCode]) => errorCodes.sortBy(_.code))
//      .toList
//      .sortBy(_._1.mkString("::::"))

    val textBuffer: mutable.ArrayBuffer[String] = new ArrayBuffer[String]()


    def inOrderTraversal(tree: ErrorGroupTree, groupPath: List[String]): Unit = {

      def handleErrorCode(errorCode: ErrorCode): Unit = {

      }


      val groupExplanation = tree.explanation.getOrElse("")
      val groupText =
        s"""Error group: ${groupPath.mkString(" / ")}
           |---------------------------------------
           |
           |$groupExplanation
           |
           |""".stripMargin

      tree.sortedErrorCodes().map(handleErrorCode)
      tree.sortedSubGroups().foreach(inOrderTraversal)

    }


    val allText = grouped.map { case (groupPath: List[String], errorCodes: Seq[ErrorCode]) =>
      val groupExplanation = errorCodes.headOption.flatMap(_.innerMostGroupExplanationO).getOrElse("")
      val groupText =
        s"""Error group: ${groupPath.mkString(" / ")}
           |---------------------------------------
           |
           |$groupExplanation
           |
           |""".stripMargin
      // ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

      val errorCodesText = errorCodes.map { e: ErrorCode =>
        val deprecatedText = e.deprecationO.fold("")(d =>
          s"""
             |    **Depreciation**: ${d}
             |    """.stripMargin)

        s"""Error code: ${e.code}
           |^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
           |    $deprecatedText
           |    **Explanation**: ${e.explanation}
           |
           |    **Category**: ${e.category}
           |
           |    **Conveyance**: ${e.conveyance}
           |
           |    **Resolution**: ${e.resolution}
           |
           |""".stripMargin

      }.mkString("\n\n")

      s"""$groupText
         |
         |$errorCodesText
         |""".stripMargin

    }.mkString("\n\n")

    allText
  }


}
