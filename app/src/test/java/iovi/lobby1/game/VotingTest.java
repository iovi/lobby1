package iovi.lobby1.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class VotingTest {

    @Test
    public void noNomineesMeansNoVote() {
        Voting v = new Voting(Collections.<Integer>emptyList(), 10, false);
        assertEquals(Voting.Phase.NO_VOTE, v.phase());
    }

    @Test
    public void singleNomineeOnFirstDayMeansNoVote() {
        Voting v = new Voting(Collections.singletonList(4), 10, true);
        assertEquals(Voting.Phase.NO_VOTE, v.phase());
    }

    @Test
    public void singleNomineeLaterLeavesAutomatically() {
        Voting v = new Voting(Collections.singletonList(4), 9, false);
        assertEquals(Voting.Phase.FINISHED, v.phase());
        assertEquals(Collections.singletonList(4), v.eliminated());
    }

    @Test
    public void clearLeaderLeaves() {
        Voting v = new Voting(Arrays.asList(3, 7, 9), 10, true);
        v.submitVotes(new int[]{2, 6, 2});
        assertEquals(Voting.Phase.FINISHED, v.phase());
        assertEquals(Collections.singletonList(7), v.eliminated());
    }

    @Test(expected = IllegalArgumentException.class)
    public void votesMustAddUpToVoters() {
        new Voting(Arrays.asList(3, 7), 10, true).submitVotes(new int[]{2, 2});
    }

    @Test
    public void tieLeadsToSpeechesAndRevoteAmongLeaders() {
        Voting v = new Voting(Arrays.asList(3, 7, 9), 10, true);
        v.submitVotes(new int[]{4, 4, 2});
        assertEquals(Voting.Phase.TIE_SPEECHES, v.phase());
        assertEquals(Arrays.asList(3, 7), v.candidates());
        assertTrue(v.isRevote());

        v.tieSpeechesDone();
        v.submitVotes(new int[]{3, 7});
        assertEquals(Voting.Phase.FINISHED, v.phase());
        assertEquals(Collections.singletonList(7), v.eliminated());
    }

    @Test
    public void repeatedTieLeadsToLiftAllVote() {
        Voting v = new Voting(Arrays.asList(3, 7), 10, true);
        v.submitVotes(new int[]{5, 5});
        v.tieSpeechesDone();
        v.submitVotes(new int[]{5, 5});
        assertEquals(Voting.Phase.LIFT_ALL, v.phase());
        assertEquals(Arrays.asList(3, 7), v.candidates());
    }

    @Test
    public void narrowerTieOnRevoteGetsAnotherRound() {
        Voting v = new Voting(Arrays.asList(1, 2, 3, 4), 10, true);
        v.submitVotes(new int[]{3, 3, 3, 1});
        assertEquals(Arrays.asList(1, 2, 3), v.candidates());
        v.tieSpeechesDone();
        v.submitVotes(new int[]{4, 4, 2});
        assertEquals(Voting.Phase.TIE_SPEECHES, v.phase());
        assertEquals(Arrays.asList(1, 2), v.candidates());
    }

    @Test
    public void liftAllWithHalfOfTableKeepsEveryone() {
        Voting v = tiedTwice(new int[]{5, 5});
        assertFalse(v.submitLiftAll(5));
        assertTrue(v.eliminated().isEmpty());
        assertEquals(Voting.Phase.FINISHED, v.phase());
    }

    @Test
    public void liftAllWithMajorityRemovesEveryone() {
        Voting v = tiedTwice(new int[]{5, 5});
        assertTrue(v.submitLiftAll(6));
        assertEquals(Arrays.asList(3, 7), v.eliminated());
    }

    @Test
    public void liftAllOnOddTable() {
        Voting v = new Voting(Arrays.asList(3, 7, 9), 9, false);
        v.submitVotes(new int[]{3, 3, 3});
        v.tieSpeechesDone();
        v.submitVotes(new int[]{3, 3, 3});
        assertEquals(Voting.Phase.LIFT_ALL, v.phase());
        assertTrue(v.submitLiftAll(5));
        assertEquals(Arrays.asList(3, 7, 9), v.eliminated());
    }

    /** Игроки 3 и 7 при 10 голосующих дважды делят голоса поровну. */
    private static Voting tiedTwice(int[] tie) {
        Voting v = new Voting(Arrays.asList(3, 7), 10, false);
        v.submitVotes(tie);
        v.tieSpeechesDone();
        v.submitVotes(tie);
        assertEquals(Voting.Phase.LIFT_ALL, v.phase());
        return v;
    }
}
