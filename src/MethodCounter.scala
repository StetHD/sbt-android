package android

import language.postfixOps

import javassist.util.proxy.{MethodHandler, MethodFilter, ProxyFactory}

import sbt._

import org.objectweb.asm._
import org.objectweb.asm.signature.SignatureVisitor

import java.io.ByteArrayOutputStream
import java.lang.reflect.Method

object MethodCounter {

  def apply(jar: File): Int = {
    var count = 0

    var classesMap: Map[Class[_], AnyRef] = Map.empty
    val classes = List(classOf[ClassVisitor], classOf[MethodVisitor], classOf[FieldVisitor], classOf[AnnotationVisitor], classOf[SignatureVisitor])
    val handler = new MethodHandler {
      override def invoke(self: AnyRef, thisMethod: Method, proceed: Method, args: Array[AnyRef]) = {
        thisMethod.getName match {
          case "visitMethod" =>
            count = count + 1
          case _ =>
        }
        val x = thisMethod.getReturnType
        if (classes.contains(x))
          classesMap(x)
        else
          null
      }
    }
    classesMap = classes.map { clazz =>
      val factory = new ProxyFactory()
      factory.setSuperclass(clazz)
      factory.setFilter(new MethodFilter {
        override def isHandled(p1: Method): Boolean = true
      })
      val o = factory.create(Array(classOf[Int]), Array(Opcodes.ASM4.asInstanceOf[AnyRef]), handler)
      (clazz, o)
    }.toMap
    classesMap(classOf[ClassVisitor]) match {
      case x: ClassVisitor =>
        val readbuf = Array.ofDim[Byte](16384)
        val buf = new ByteArrayOutputStream

        Using.fileInputStream(jar) (Using.jarInputStream(_) { jin =>
          Iterator.continually(jin.getNextJarEntry) takeWhile (_ != null) foreach {
            entry =>
              if (entry.getName.endsWith(".class")) {
                buf.reset()
                Iterator.continually(jin.read(readbuf)) takeWhile (
                  _ != -1) foreach (buf.write(readbuf, 0, _))
                val r = new ClassReader(buf.toByteArray)
                r.accept(x, 0)
              }
              jin.closeEntry()
          }

        })
    }

    count
  }
}

