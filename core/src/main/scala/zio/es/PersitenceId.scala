package zio.es

opaque type PersitenceId = String

object PersitenceId {
  def apply(value: String): PersitenceId = value
  def toString(id: PersitenceId): String = id
}

extension (id: PersitenceId)
  def toString: String = id
