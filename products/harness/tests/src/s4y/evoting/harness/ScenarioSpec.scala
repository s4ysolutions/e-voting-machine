package s4y.evoting.harness

import org.scalatest.funsuite.AnyFunSuite
import s4y.evoting.ballots.domain.vo.BallotError
import s4y.evoting.casting.domain.vo.CastError

/** The whole system end to end, through the real wiring (design doc flows F2–F7, F8). */
class ScenarioSpec extends AnyFunSuite:

  test("F2-F3: voters get ballots, cast, and the audit passes"):
    scenario {
      val alice = voter("alice@x.org")
      val bob   = voter("bob@x.org")
      assert(alice.getsBallot().isRight)
      assert(bob.getsBallot().isRight)
      assert(alice.casts("budget" -> "yes", "board" -> "bob").isRight)
      assert(bob.casts("budget" -> "no", "board" -> "bob").isRight)
      assert(tally("budget") == Map("yes" -> 1, "no" -> 1))
      assert(audit().allPassed, audit().checks)
    }

  test("F4 / F7: after a re-vote only the last vote counts, and the old receipt is still listed"):
    scenario {
      val alice   = voter("alice@x.org")
      alice.getsBallot()
      val coerced = alice.casts("budget" -> "yes", "board" -> "bob").toOption.get
      assert(alice.revotes().isRight)
      assert(alice.casts("budget" -> "no", "board" -> "alice").isRight)
      assert(tally("budget") == Map("yes" -> 0, "no" -> 1))
      val report = audit()
      assert(report.allPassed, report.checks)
      // What a coercer sees: the receipt they were shown is still in the ledger, unchanged.
      val ledger = summon[Scenario].system.publicRecord(summon[Scenario].voting).toOption.flatten.get.ledger
      assert(coerced.entries.forall(ledger.contains))
    }

  test("X1: a voter gets one first ballot; strangers get none"):
    scenario {
      val alice = voter("alice@x.org")
      assert(alice.getsBallot().isRight)
      assert(alice.getsBallot().left.toOption.contains(BallotError.AlreadyIssued(summon[Scenario].voting)))
      assert(voter("mallory@x.org").getsBallot().left.toOption.contains(BallotError.NotEligible(summon[Scenario].voting)))
      assert(audit().allPassed, audit().checks)
    }

  test("X7: after the voting closes, nothing is cast"):
    scenario {
      val alice = voter("alice@x.org")
      alice.getsBallot()
      closeVoting()
      assert(alice.casts("budget" -> "yes", "board" -> "bob").left.toOption.contains(CastError.VotingNotOpen(summon[Scenario].voting)))
    }
