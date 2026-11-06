package com.arrl.radiocraft.datagen.lang;

import net.neoforged.neoforge.common.data.LanguageProvider;

import java.util.function.BiConsumer;

/** The License Study program's words (computer apps 2, 2026-11-06): {@code screen.radiocraft.computer.study.*} and its errors. */
public final class LicenseStudyLang {

	private LicenseStudyLang() {}

	public static void add(LanguageProvider provider) {
		BiConsumer<String, String> s = (key, text) -> provider.add("screen.radiocraft.computer." + key, text);
		s.accept("err.study_no_pools", "No question pools are loaded on this server");
		s.accept("err.study_unknown_pool", "That question pool is not on this server any more");
		s.accept("err.study_unknown_question", "That question is not in the pool any more");
		s.accept("err.study_nothing", "No questions to study there");

		s.accept("study.title", "License Study");
		s.accept("study.keys_menu", "S=Study  E=Practice exam  P=Progress  Esc=Exit");
		s.accept("study.no_pools_hint", "Ask the server's admin: the pools are JSON files in data/*/license_pools or config/radiocraft/license_pools.");
		s.accept("study.intro", "Pick a question pool. Your progress is yours, on any computer.");
		s.accept("study.col_pool", "Pool");
		s.accept("study.col_element", "Element");
		s.accept("study.col_questions", "Qs");
		s.accept("study.col_seen", "Seen");
		s.accept("study.col_mastered", "Known");
		s.accept("study.col_pass", "Pass");
		s.accept("study.col_sub", "Sub");
		s.accept("study.col_name", "Subelement");
		s.accept("study.valid", "%s, pool valid %s");
		s.accept("study.exam_line", "Exam: %s questions, one from each group; %s right to pass");
		s.accept("study.questions_line", "%s questions to study (%s need a figure and are left out)");
		s.accept("study.aptitude", "Aptitude: about %s of %s right on an exam now; pass likelihood %s");
		s.accept("study.mastered_line", "%s of %s questions known well");
		s.accept("study.all_subelements", "All subelements");
		s.accept("study.pick_title", "Study");
		s.accept("study.pick", "Study which part of the %s pool?");

		s.accept("study.title_study", "License Study - %s");
		s.accept("study.keys_card", "A-D=Answer  I=I don't know  P=Progress  Esc=Menu");
		s.accept("study.keys_answered", "Enter=Next card  P=Progress  Esc=Menu");
		s.accept("study.session", "This session %s/%s");
		s.accept("study.loading", "Reading the question pool...");
		s.accept("study.new_card", "New question");
		s.accept("study.level", "Level %s, answered %s times");
		s.accept("study.right", "Correct!");
		s.accept("study.wrong_answer", "Wrong: the answer is %s.");
		s.accept("study.idk_answer", "The answer is %s.");
		s.accept("study.again", "This card comes back after %s more cards.");
		s.accept("study.refs", "Rules: %s");

		s.accept("study.title_exam", "Practice Exam - %s");
		s.accept("study.title_review", "Exam Review - %s");
		s.accept("study.keys_exam", "A-D=Answer  Left/Right=Question  F10=Grade  Esc=Quit");
		s.accept("study.keys_review", "Left/Right=Question  W=Next wrong  Esc=Menu");
		s.accept("study.question_of", "Question %s of %s");
		s.accept("study.answered", "Answered %s of %s; %s right needed to pass");
		s.accept("study.skipped", "Left out (figures): %s");
		s.accept("study.grade_title", "Grade the exam");
		s.accept("study.grade_confirm", "Hand in the exam for grading?");
		s.accept("study.grade_unanswered", "%s questions have no answer and count as wrong. Hand in the exam anyway?");
		s.accept("study.passed_title", "Passed");
		s.accept("study.failed_title", "Not this time");
		s.accept("study.passed", "PASS: %s of %s right (%s needed). Congratulations!");
		s.accept("study.failed", "FAIL: %s of %s right (%s needed). Study the ones marked x.");
		s.accept("study.leave_exam_title", "Quit the exam");
		s.accept("study.leave_exam", "Leave this exam without grading it?");

		s.accept("study.title_progress", "Progress - %s");
		s.accept("study.keys_progress", "R=Reset this pool  Esc=Menu");
		s.accept("study.pass_needs", "An exam takes one question from each group: %s of %s right passes.");
		s.accept("study.answers_line", "Answers so far: %s (%s right, %s wrong, %s I don't know)");
		s.accept("study.legend", "Bars: yellow seen, green known well (answered right three times running)");
		s.accept("study.reset_title", "Reset progress");
		s.accept("study.reset", "Forget all your cards in the %s pool and start again?");
	}
}
