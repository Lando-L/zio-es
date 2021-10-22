package zio.es

opaque type PersistenceId = String

object PersistenceId:
  def apply(value: String): PersistenceId = value
  def toString(id: PersistenceId): String = id
