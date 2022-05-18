package fix

import scalafix.v1._
import scala.meta._
import metaconfig._

case class Scala3CommunityBuildMillAdapterConfig(
    targetScalaVersion: Option[String] = None,
    targetPublishVersion: Option[String] = None
)
object Scala3CommunityBuildMillAdapterConfig {
  def default = Scala3CommunityBuildMillAdapterConfig()
  implicit val surface =
    metaconfig.generic.deriveSurface[Scala3CommunityBuildMillAdapterConfig]
  implicit val decoder =
    metaconfig.generic.deriveDecoder(default)
}

class Scala3CommunityBuildMillAdapter(config: Scala3CommunityBuildMillAdapterConfig)
    extends SyntacticRule("Scala3CommunityBuildMillAdapter") {
  def this() = this(config = Scala3CommunityBuildMillAdapterConfig())
  override def withConfiguration(config: Configuration): Configured[Rule] = {
    config.conf
      .getOrElse("Scala3CommunityBuildMillAdapter") {
        def propOrDefault(
            prop: String,
            default: Scala3CommunityBuildMillAdapterConfig => Option[String]
        ) = sys.props
          .get(prop)
          .filter(_.nonEmpty)
          .orElse(default(this.config))

        this.config
          .copy(
            targetScalaVersion = propOrDefault("communitybuild.scala", _.targetScalaVersion),
            targetPublishVersion = propOrDefault("communitybuild.version", _.targetPublishVersion)
          )
      }
      .map(new Scala3CommunityBuildMillAdapter(_))
  }

  override def fix(implicit doc: SyntacticDocument): Patch = {
    val headerInject = {
      if (sys.props.contains("communitybuild.noInjects")) Patch.empty
      else Patch.addLeft(doc.tree, Replacment.MillCommunityBuildInject)
    }
    val patch = doc.tree.collect {
      case init @ Init(
            Type.Apply(name @ WithTypeName("Cross"), List(tpeParam)),
            _,
            List(args)
          ) =>
        List(
          Patch.replaceTree(name, Replacment.CommunityBuildCross),
          Patch.addRight(
            init,
            s"(${Replacment.ScalaVersion("sys.error(\"targetScalaVersion not specified in scalafix\")", asTarget = false)})"
          )
        ).asPatch

      case template @ Template(_, traits, _, _)
          if anyTreeOfTypeName(
            has = Seq("ScalaModule", "JavaModule"),
            butNot = Seq("PublishModule", "CoursierModule")
          )(traits) =>
        Patch.addRight(traits.last, s" with ${Replacment.CommunityBuildCoursierModule}")

      case Init(name @ WithTypeName("CoursierModule"), _, _) =>
        Patch.replaceTree(name, Replacment.CommunityBuildCoursierModule)

      case Init(name @ WithTypeName("PublishModule"), _, _) =>
        Patch.replaceTree(name, Replacment.CommunityBuildPublishModule)

      case ValOrDefDef(Term.Name("scalaVersion"), body) =>
        Patch.replaceTree(body, Replacment.ScalaVersion(body.toString))

      case ValOrDefDef(Term.Name("publishVersion"), body) =>
        Patch.replaceTree(body, Replacment.PublishVersion(body.toString))

    }.asPatch

    headerInject + patch
  }

  object Replacment {
    val CommunityBuildCross = "MillCommunityBuildCross"
    val CommunityBuildPublishModule = "MillCommunityBuild.CommunityBuildPublishModule"
    val CommunityBuildCoursierModule = "MillCommunityBuild.CommunityBuildCoursierModule"
    def ScalaVersion(default: => String, asTarget: Boolean = true) =
      config.targetScalaVersion
        .map(quoted(_))
        .map(v => if (asTarget) s"mill.T($v)" else v)
        .getOrElse(default)
    def PublishVersion(default: => String) = config.targetPublishVersion
      .map(quoted(_))
      .map(v => s"mill.T($v)")
      .getOrElse(default)

    private def quoted(v: String): String = {
      // Make sure that literal is quoted
      val quote = "\""
      val stripQutoes = v.stripPrefix(quote).stripSuffix(quote)
      quote + stripQutoes + quote
    }
    val MillCommunityBuildInject = """
    |import $file.MillCommunityBuild
    |// Main entry point for community build
    |def runCommunityBuild(_evaluator: mill.eval.Evaluator, scalaVersion: String, configJson: String, targets: String*) = T.command {
    |  implicit val ctx = MillCommunityBuild.Ctx(this, scalaVersion, _evaluator, T.log)
    |  MillCommunityBuild.runBuild(configJson, targets)
    |}
    |
    |// Replaces mill.define.Cross allowing to use map used cross versions
    |class MillCommunityBuildCross[T: _root_.scala.reflect.ClassTag]
    |  (cases: _root_.scala.Any*)
    |  (buildScalaVersion: _root_.java.lang.String)
    |  (implicit ci:  _root_.mill.define.Cross.Factory[T], ctx: _root_.mill.define.Ctx) 
    |  extends _root_.mill.define.Cross[T](
    |      MillCommunityBuild.mapCrossVersions(buildScalaVersion, cases): _*
    |    )
    |// End of code injects
    |""".stripMargin

  }

  object ValOrDefDef {
    def unapply(tree: Tree): Option[(Term.Name, Term)] = tree match {
      // Make sure def has no parameter lists 
      case Defn.Def(_, name, _, Nil, _, body)         => Some(name -> body)
      case Defn.Val(_, Pat.Var(name) :: Nil, _, body) => Some(name -> body)
      case Defn.Var(_, Pat.Var(name) :: Nil, _, Some(body)) =>
        Some(name -> body)
      case _ => None
    }
  }

  def anyTreeOfTypeName(has: Seq[String], butNot: Seq[String] = Nil)(
      trees: List[Tree]
  ): Boolean = {
    val exists = trees.exists {
      case WithTypeName(name) => has.contains(name)
      case _                  => false
    }
    val existsBlocker = butNot.nonEmpty && trees.exists {
      case WithTypeName(name) => butNot.contains(name)
      case _                  => false
    }
    exists && !existsBlocker
  }

  object WithTypeName {
    def unapply(tree: Tree): Option[String] = tree match {
      case name: Type.Name         => Some(name.toString)
      case Type.Select(qual, name) => Some(name.toString)
      case Init(name, _, _)        => unapply(name)
      case _                       => None
    }
  }
}
