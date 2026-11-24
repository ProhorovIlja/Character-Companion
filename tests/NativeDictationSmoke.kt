package dev.caicompanion.app

import java.util.UUID

fun main() {
    fun id(n: Long) = UUID(0,n).toString()
    val key = NativeChatWorkspace.Key(id(1),id(2))
    val ticket = NativeChatWorkspace.Ticket(key, UUID.randomUUID())
    val stale = ticket.copy(nonce = UUID.randomUUID())
    val other = NativeChatWorkspace.Ticket(NativeChatWorkspace.Key(id(3),id(4)), UUID.randomUUID())
    val d = NativeDictation()
    var count = 0
    fun verify(ok: Boolean) { check(ok); count++ }
    fun bad(block: () -> Unit) = verify(runCatching(block).isFailure)
    fun begin(text: String = "", send: Boolean = true) = d.begin(ticket,text,text.length,text.length,send)
    verify(!d.active)
    val a = begin("Hello")
    verify(d.active)
    bad { begin() }
    verify(d.stop(a,false))
    verify(!d.stop(a,true))
    val r = d.complete(a,ticket,"Hello",listOf("world 🌙"))!!
    verify(r.edit.text == "Hello\nworld 🌙" && !r.requestSend)
    verify(!d.active && d.complete(a,ticket,"Hello",listOf("again")) == null)
    val b = begin(r.edit.text)
    verify(d.stop(b,true))
    val second = d.complete(b,ticket,r.edit.text,listOf("ещё"))!!
    verify(second.requestSend && second.edit.text == "Hello\nworld 🌙\nещё")
    val c = begin()
    verify(!d.complete(c,ticket,"",listOf("natural end"))!!.requestSend)
    val e = begin(send = false)
    verify(!d.stop(e,true) && d.stop(e,false))
    verify(!d.complete(e,ticket,"",listOf("draft only"))!!.requestSend)
    val f = begin("old")
    verify(d.complete(f,stale,"old",listOf("late")) == null && !d.active)
    val g = begin("old")
    verify(d.complete(g,other,"old",listOf("late")) == null)
    val h = begin("old")
    verify(d.complete(h,ticket,"changed",listOf("late")) == null)
    val j = begin()
    verify(!d.cancel(a) && d.active)
    verify(d.cancel(j) && !d.active && d.complete(j,ticket,"",listOf("late")) == null)
    val k = begin()
    verify(d.complete(a,ticket,"",listOf("stale")) == null && d.active)
    verify(d.complete(k,ticket,"",listOf("", "\u0000", "valid"))!!.edit.text == "valid")
    val l = begin("x".repeat(15999))
    verify(d.stop(l,true))
    verify(d.complete(l,ticket,"x".repeat(15999),listOf("too long")) == null && !d.active)
    val m = d.begin(ticket,"a🌙b",2,2,true)
    val edit = d.complete(m,ticket,"a🌙b",listOf("word"))!!.edit
    verify(edit.text == "a🌙 word b" && VoiceDraftPolicy.validText(edit.text))
    bad { d.begin(ticket,"a",2,2,true) }
    bad { begin("\uD800") }
    val n = begin()
    verify(d.stop(n,true))
    verify(d.complete(n,ticket,"",listOf(" ")) == null && !d.active)
    val o = begin()
    verify(d.cancel(o))
    println("NativeDictation: $count checks passed")
}
