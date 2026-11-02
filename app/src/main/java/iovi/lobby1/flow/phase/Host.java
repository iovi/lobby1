package iovi.lobby1.flow.phase;

import iovi.lobby1.flow.GameUi;
import iovi.lobby1.flow.Music;
import iovi.lobby1.flow.Rules;
import iovi.lobby1.flow.Scheduler;
import iovi.lobby1.flow.Voice;
import iovi.lobby1.game.Game;

/** Всё, чем пользуются фазы: партия, экраны, голос, музыка, фолы и итоги выбывших. */
public final class Host {
    final Game game;
    final GameUi ui;
    final Voice voice;
    final Music music;
    final Scheduler scheduler;
    /** Отсчёт ночного хода — из настроек. */
    final long nightTurnMs;
    /** Версия правил — из настроек. */
    public final Rules rules;
    public final Screens screens;
    public final Fouls fouls;
    public final Summaries summaries = new Summaries();

    public Host(Game game, GameUi ui, Voice voice, Music music, Scheduler scheduler, long nightTurnMs,
                Rules rules, Fouls.Listener onFoulRemoval) {
        this.game = game;
        this.ui = ui;
        this.voice = voice;
        this.music = music;
        this.scheduler = scheduler;
        this.nightTurnMs = nightTurnMs;
        this.rules = rules;
        this.screens = new Screens(ui, voice, scheduler);
        this.fouls = new Fouls(game, ui, voice, screens, rules, onFoulRemoval);
    }
}